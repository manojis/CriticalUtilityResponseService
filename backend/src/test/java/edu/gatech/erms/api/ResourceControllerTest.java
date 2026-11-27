package edu.gatech.erms.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.gatech.erms.domain.PageResult;
import edu.gatech.erms.domain.ResourceSearchRow;
import edu.gatech.erms.service.ResourceService;
import edu.gatech.erms.support.ErmsWebMvcTest;
import edu.gatech.erms.support.TestData;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ErmsWebMvcTest(ResourceController.class)
class ResourceControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private ResourceService resourceService;

  @Test
  void createReturns201WithDetail() throws Exception {
    when(resourceService.create(eq("acme"), any())).thenReturn(TestData.resource(1001, "acme"));

    mvc.perform(
            post("/api/v1/resources")
                .with(jwt().jwt(j -> j.subject("acme")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"resourceName":"Hydraulic Crane","model":"XL-200","primaryEsf":3,
                     "additionalEsfs":[1],"capabilities":["Lift 20t"],"cost":150.00,
                     "costUnit":"Hour","latitude":33.77,"longitude":-84.39,"maxDistanceKm":120}
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/resources/1001"))
        .andExpect(jsonPath("$.resourceId").value(1001))
        .andExpect(jsonPath("$.primaryEsf.esfNumber").value(3))
        .andExpect(jsonPath("$.additionalEsfs[0].description").value("Transportation"))
        .andExpect(jsonPath("$.capabilities[0]").value("Lift 20t"))
        .andExpect(jsonPath("$.cost").value(150.00))
        .andExpect(jsonPath("$.latitude").value(33.77))
        .andExpect(jsonPath("$.maxDistanceKm").value(120.0))
        .andExpect(jsonPath("$.available").value(true))
        .andExpect(jsonPath("$.nextAvailableDate").doesNotExist());
  }

  @Test
  void createValidatesNumbers_F22() throws Exception {
    mvc.perform(
            post("/api/v1/resources")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"resourceName":"ab","primaryEsf":0,"cost":-1.005,"costUnit":"",
                     "latitude":91,"longitude":181,"maxDistanceKm":0}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.field=='resourceName')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field=='primaryEsf')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field=='cost')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field=='costUnit')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field=='latitude')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field=='longitude')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field=='maxDistanceKm')]").exists());
  }

  @Test
  void allSearchParamsAreOptional_F21() throws Exception {
    when(resourceService.search("alice", null, null, null, null, 0, 20))
        .thenReturn(
            new PageResult<>(
                List.of(
                    new ResourceSearchRow(
                        1001,
                        "Hydraulic Crane",
                        "acme",
                        "Acme",
                        new BigDecimal("150.00"),
                        "Hour",
                        TestData.TODAY.plusDays(3),
                        null)),
                0,
                20,
                1));

    mvc.perform(get("/api/v1/resources").with(jwt().jwt(j -> j.subject("alice"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].resourceId").value(1001))
        .andExpect(jsonPath("$.content[0].available").value(false))
        .andExpect(jsonPath("$.content[0].nextAvailableDate").value("2026-11-28"))
        .andExpect(jsonPath("$.content[0].distanceKm").doesNotExist())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  void searchPassesFilters() throws Exception {
    when(resourceService.search(
            eq("alice"), eq("crane"), eq(3), eq("MD-1"), any(BigDecimal.class), eq(2), eq(5)))
        .thenReturn(new PageResult<>(List.of(), 2, 5, 0));

    mvc.perform(
            get("/api/v1/resources")
                .param("keyword", "crane")
                .param("esf", "3")
                .param("incidentId", "MD-1")
                .param("distanceKm", "50")
                .param("page", "2")
                .param("size", "5")
                .with(jwt().jwt(j -> j.subject("alice"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(2));
    verify(resourceService)
        .search(
            eq("alice"), eq("crane"), eq(3), eq("MD-1"), eq(new BigDecimal("50")), eq(2), eq(5));
  }

  @Test
  void typeMismatchIs400NotA200_F20() throws Exception {
    mvc.perform(get("/api/v1/resources").param("esf", "abc").with(jwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("request.malformed"));
    mvc.perform(get("/api/v1/resources/abc").with(jwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("request.malformed"));
  }

  @Test
  void pageSizeIsBounded() throws Exception {
    mvc.perform(get("/api/v1/resources").param("size", "101").with(jwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation.failed"))
        .andExpect(jsonPath("$.errors[0].field").value("size"));
  }

  @Test
  void unexpectedErrorsAreGeneric500_S6() throws Exception {
    when(resourceService.get(1001))
        .thenThrow(new IllegalStateException("jdbc:mysql://db:3306 password=hunter2"));

    mvc.perform(get("/api/v1/resources/1001").with(jwt()))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("internal.error"))
        .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
        .andExpect(jsonPath("$.trace").doesNotExist());
  }

  @Test
  void getResource() throws Exception {
    when(resourceService.get(1001)).thenReturn(TestData.resource(1001, "acme"));
    mvc.perform(get("/api/v1/resources/1001").with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ownerUsername").value("acme"));
    verify(resourceService).get(1001);
  }

  @Test
  void unknownPathIs404ProblemDetail() throws Exception {
    mvc.perform(get("/api/v1/nope").with(jwt()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("resource.not_found"))
        .andExpect(jsonPath("$.detail").value("No endpoint matches the request path"));
  }

  @Test
  void wrongMethodIs405ProblemDetail() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/v1/resources/1001")
                .with(jwt()))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("request.method_not_allowed"));
  }

  @Test
  void searchKeywordIsPassedThroughUntrimmedToService() throws Exception {
    when(resourceService.search(
            eq("alice"), eq("50%"), isNull(), isNull(), isNull(), eq(0), eq(20)))
        .thenReturn(new PageResult<>(List.of(), 0, 20, 0));
    mvc.perform(
            get("/api/v1/resources")
                .param("keyword", "50%")
                .with(jwt().jwt(j -> j.subject("alice"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }
}
