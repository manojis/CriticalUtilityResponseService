package edu.gatech.erms.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.gatech.erms.domain.Incident;
import edu.gatech.erms.service.CreateIncidentCommand;
import edu.gatech.erms.service.IncidentService;
import edu.gatech.erms.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IncidentIT extends AbstractIntegrationTest {

  @Autowired private IncidentService incidentService;

  @Test
  void latitudeAndLongitudeRoundTripUnchanged_F7() throws Exception {
    createUser("alice");

    postJson(
            "alice",
            "/api/v1/incidents",
            """
            {"declarationAbbreviation":"MD","incidentDate":"%s","description":"Flooding on I-75",
             "latitude":33.749012,"longitude":-84.388034}
            """
                .formatted(today()))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/incidents/MD-1"))
        .andExpect(jsonPath("$.incidentId").value("MD-1"))
        .andExpect(jsonPath("$.latitude").value(33.749012))
        .andExpect(jsonPath("$.longitude").value(-84.388034));

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT latitude, longitude FROM incident WHERE incident_id = 'MD-1'", Map.of());
    assertThat((BigDecimal) row.get("latitude")).isEqualByComparingTo("33.749012");
    assertThat((BigDecimal) row.get("longitude")).isEqualByComparingTo("-84.388034");

    mvc.perform(as("alice", get("/api/v1/incidents/MD-1")))
        .andExpect(jsonPath("$.latitude").value(33.749012))
        .andExpect(jsonPath("$.longitude").value(-84.388034));
  }

  @Test
  void tenConcurrentCreatesGetTenDistinctSequentialIds_F8() throws Exception {
    createUser("alice");
    int threads = 10;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Incident>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      Callable<Incident> task =
          () -> {
            start.await();
            return incidentService.create(
                "alice",
                new CreateIncidentCommand(
                    "MD", today(), "Concurrent incident", BigDecimal.ONE, BigDecimal.ONE));
          };
      futures.add(pool.submit(task));
    }
    start.countDown();
    List<String> ids = new ArrayList<>();
    for (Future<Incident> f : futures) {
      ids.add(f.get(60, TimeUnit.SECONDS).incidentId());
    }
    pool.shutdown();

    assertThat(ids).doesNotHaveDuplicates().hasSize(10);
    assertThat(ids)
        .containsExactlyInAnyOrder(
            "MD-1", "MD-2", "MD-3", "MD-4", "MD-5", "MD-6", "MD-7", "MD-8", "MD-9", "MD-10");
    assertThat(
            jdbc.queryForObject(
                "SELECT next_value FROM incident_sequence WHERE declaration_abbreviation = 'MD'",
                Map.of(),
                Integer.class))
        .isEqualTo(11);
  }

  @Test
  void idsAreSequentialPerDeclarationAndClientIdIsIgnored_F8() throws Exception {
    createUser("alice");
    assertThat(createIncident("alice", "MD", 1, 1)).isEqualTo("MD-1");
    assertThat(createIncident("alice", "ED", 1, 1)).isEqualTo("ED-1");
    assertThat(createIncident("alice", "MD", 1, 1)).isEqualTo("MD-2");
    postJson(
            "alice",
            "/api/v1/incidents",
            """
            {"incidentId":"MD-1","declarationAbbreviation":"md","incidentDate":"%s",
             "description":"Lowercase abbreviation","latitude":1,"longitude":1}
            """
                .formatted(today()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.incidentId").value("MD-3"))
        .andExpect(jsonPath("$.declarationAbbreviation").value("MD"));
  }

  @Test
  void incidentsAreVisibleToTheirOwnerOnly_S3() throws Exception {
    createUser("alice");
    createUser("bob");
    String id = createIncident("alice", "MD", 1, 1);
    createIncident("bob", "FD", 2, 2);

    mvc.perform(as("bob", get("/api/v1/incidents/" + id)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("resource.not_found"));
    mvc.perform(as("alice", get("/api/v1/incidents")))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].incidentId").value(id))
        .andExpect(jsonPath("$[0].ownerUsername").value("alice"));
  }

  @Test
  void listIsNewestDateFirst() throws Exception {
    createUser("alice");
    postJson(
        "alice",
        "/api/v1/incidents",
        """
        {"declarationAbbreviation":"ED","incidentDate":"%s","description":"Older incident",
         "latitude":1,"longitude":1}
        """
            .formatted(today().minusDays(10)));
    createIncident("alice", "MD", 1, 1);

    mvc.perform(as("alice", get("/api/v1/incidents")))
        .andExpect(jsonPath("$[0].incidentId").value("MD-1"))
        .andExpect(jsonPath("$[1].incidentId").value("ED-1"));
  }

  @Test
  void futureDateAndUnknownDeclarationAreRejected() throws Exception {
    createUser("alice");
    postJson(
            "alice",
            "/api/v1/incidents",
            """
            {"declarationAbbreviation":"MD","incidentDate":"%s","description":"Tomorrow's incident",
             "latitude":1,"longitude":1}
            """
                .formatted(today().plusDays(1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("incidentDate"));
    postJson(
            "alice",
            "/api/v1/incidents",
            """
            {"declarationAbbreviation":"ZZ","incidentDate":"%s","description":"Unknown declaration",
             "latitude":1,"longitude":1}
            """
                .formatted(today()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("declarationAbbreviation"));
  }
}
