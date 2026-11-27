package edu.gatech.erms.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.gatech.erms.repository.ResourceRepository;
import edu.gatech.erms.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class ResourceIT extends AbstractIntegrationTest {

  @Test
  void createPersistsResourceCapabilitiesAndEsfs() throws Exception {
    createUser("acme");

    postJson(
            "acme",
            "/api/v1/resources",
            """
            {"resourceName":"Hydraulic Crane","model":"XL-200","primaryEsf":3,"additionalEsfs":[7,1],
             "capabilities":["Lift 20t"," lift 20T ","Night ops"],"cost":150,"costUnit":"hour",
             "latitude":33.77,"longitude":-84.39,"maxDistanceKm":120}
            """)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.resourceName").value("Hydraulic Crane"))
        .andExpect(jsonPath("$.ownerUsername").value("acme"))
        .andExpect(jsonPath("$.ownerName").value("Name of acme"))
        .andExpect(jsonPath("$.primaryEsf.description").value("Public Works and Engineering"))
        .andExpect(jsonPath("$.additionalEsfs[0].esfNumber").value(1))
        .andExpect(jsonPath("$.additionalEsfs[1].esfNumber").value(7))
        .andExpect(jsonPath("$.capabilities.length()").value(2))
        .andExpect(jsonPath("$.costUnit").value("Hour"))
        .andExpect(jsonPath("$.cost").value(150.00))
        .andExpect(jsonPath("$.maxDistanceKm").value(120.0))
        .andExpect(jsonPath("$.available").value(true));
  }

  @Test
  void omittedListsAreAccepted_R7() throws Exception {
    createUser("acme");
    long id =
        ((Number)
                read(
                    postJson(
                            "acme",
                            "/api/v1/resources",
                            """
                            {"resourceName":"Bare Truck","primaryEsf":1,"cost":0,"costUnit":"Day",
                             "latitude":0,"longitude":0}
                            """)
                        .andExpect(status().isCreated())
                        .andReturn(),
                    "$.resourceId"))
            .longValue();

    mvc.perform(as("someone", get("/api/v1/resources/" + id)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities.length()").value(0))
        .andExpect(jsonPath("$.additionalEsfs.length()").value(0))
        .andExpect(jsonPath("$.model").doesNotExist())
        .andExpect(jsonPath("$.maxDistanceKm").doesNotExist());
  }

  @Test
  void invalidReferencesAreFieldErrors() throws Exception {
    createUser("acme");
    postJson(
            "acme",
            "/api/v1/resources",
            """
            {"resourceName":"Bad Refs","primaryEsf":16,"additionalEsfs":[16,16],"cost":1,
             "costUnit":"Fortnight","latitude":0,"longitude":0}
            """)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation.failed"))
        .andExpect(jsonPath("$.errors[?(@.field=='primaryEsf')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field=='additionalEsfs')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field=='costUnit')]").exists());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resource", Map.of(), Integer.class))
        .isZero();
  }

  @Test
  void unknownResourceIs404() throws Exception {
    mvc.perform(as("alice", get("/api/v1/resources/999999")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Resource 999999 not found"));
  }

  /** Legacy defect R6: a failure while saving ESFs must roll back the resource and capabilities. */
  @Nested
  class Atomicity extends AbstractIntegrationTest {

    @MockitoSpyBean private ResourceRepository resourceRepository;

    @Test
    void failureAfterResourceInsertRollsBackEverything_R6() throws Exception {
      createUser("acme");
      doThrow(new DataAccessResourceFailureException("simulated failure"))
          .when(resourceRepository)
          .insertAdditionalEsfs(anyLong(), anyList());

      postJson(
              "acme",
              "/api/v1/resources",
              """
              {"resourceName":"Doomed Crane","primaryEsf":3,"additionalEsfs":[1],
               "capabilities":["Lift 20t"],"cost":1,"costUnit":"Hour","latitude":0,"longitude":0}
              """)
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));

      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resource", Map.of(), Integer.class))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM resource_capability", Map.of(), Integer.class))
          .isZero();
      List<String> names =
          jdbc.queryForList("SELECT resource_name FROM resource", Map.of(), String.class);
      assertThat(names).doesNotContain("Doomed Crane");
    }
  }
}
