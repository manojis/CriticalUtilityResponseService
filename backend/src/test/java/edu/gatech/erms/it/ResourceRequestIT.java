package edu.gatech.erms.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.gatech.erms.domain.UserType;
import edu.gatech.erms.support.AbstractIntegrationTest;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Request lifecycle, DB constraints and legacy defects F12–F18, S3, R3. */
class ResourceRequestIT extends AbstractIntegrationTest {

  private String incident;
  private long crane;

  @BeforeEach
  void setUpData() throws Exception {
    createUser("alice");
    createUser("acme", UserType.COMPANY);
    createUser("mallory");
    incident = createIncident("alice", "MD", 33.749, -84.388);
    crane = createResource("acme", "Hydraulic Crane", 33.77, -84.39);
  }

  private String dbStatus(long requestId) {
    return jdbc.queryForObject(
        "SELECT status FROM resource_request WHERE request_id = :id",
        Map.of("id", requestId),
        String.class);
  }

  @Test
  void fullLifecycleRequestDeployReturn_R3() throws Exception {
    postJson(
            "alice",
            "/api/v1/resource-requests",
            """
            {"resourceId":%d,"incidentId":"%s","expectedReturnDate":"%s"}
            """
                .formatted(crane, incident, today().plusDays(5)))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", Matchers.startsWith("/api/v1/resource-requests/")))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.requestDate").value(today().toString()))
        .andExpect(jsonPath("$.requestedBy").value("alice"))
        .andExpect(jsonPath("$.ownerUsername").value("acme"))
        .andExpect(jsonPath("$.incidentDescription").value("Incident near alice"))
        .andExpect(jsonPath("$.resourceAvailable").value(true));
    long id =
        jdbc.queryForObject("SELECT MAX(request_id) FROM resource_request", Map.of(), Long.class);

    action("acme", id, "deploy")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DEPLOYED"))
        .andExpect(jsonPath("$.deployDate").value(today().toString()))
        .andExpect(jsonPath("$.resourceAvailable").value(false));
    mvc.perform(as("alice", get("/api/v1/resources/" + crane)))
        .andExpect(jsonPath("$.available").value(false))
        .andExpect(jsonPath("$.nextAvailableDate").value(today().plusDays(5).toString()));

    action("alice", id, "return")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RETURNED"))
        .andExpect(jsonPath("$.actualReturnDate").value(today().toString()))
        .andExpect(jsonPath("$.resourceAvailable").value(true));
    mvc.perform(as("alice", get("/api/v1/resources/" + crane)))
        .andExpect(jsonPath("$.available").value(true));
  }

  @Test
  void illegalTransitionsAre409() throws Exception {
    long id = requestResource("alice", crane, incident, false);
    action("alice", id, "return").andExpect(status().isConflict());
    action("acme", id, "deploy").andExpect(status().isOk());
    action("alice", id, "cancel")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("state.conflict"))
        .andExpect(jsonPath("$.detail").value("Request " + id + " is DEPLOYED; cannot cancel"));
    action("acme", id, "reject").andExpect(status().isConflict());
    action("acme", id, "deploy").andExpect(status().isConflict());
  }

  @Test
  void returnOnlyAffectsTheTargetRequest_F12() throws Exception {
    String second = createIncident("alice", "ED", 33.7, -84.3);
    long first = requestResource("alice", crane, incident, false);
    action("acme", first, "deploy").andExpect(status().isOk());
    action("alice", first, "return").andExpect(status().isOk());
    long old = first;
    long current = requestResource("alice", crane, second, false);
    action("acme", current, "deploy").andExpect(status().isOk());
    String oldReturnDate =
        jdbc.queryForObject(
            "SELECT actual_return_date FROM resource_request WHERE request_id = :id",
            Map.of("id", old),
            String.class);
    long pendingOther = requestResource("alice", crane, incident, false);

    action("acme", current, "return").andExpect(status().isOk());

    assertThat(dbStatus(current)).isEqualTo("RETURNED");
    assertThat(dbStatus(pendingOther)).isEqualTo("PENDING");
    assertThat(
            jdbc.queryForObject(
                "SELECT actual_return_date FROM resource_request WHERE request_id = :id",
                Map.of("id", pendingOther),
                String.class))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT actual_return_date FROM resource_request WHERE request_id = :id",
                Map.of("id", old),
                String.class))
        .isEqualTo(oldReturnDate);
  }

  @Test
  void resourceCannotBeDeployedTwice_F13() throws Exception {
    createUser("bob");
    String bobsIncident = createIncident("bob", "FD", 33.7, -84.3);
    long aliceReq = requestResource("alice", crane, incident, false);
    long bobReq = requestResource("bob", crane, bobsIncident, false);

    action("acme", aliceReq, "deploy").andExpect(status().isOk());
    action("acme", bobReq, "deploy")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Resource " + crane + " is currently deployed"));
    mvc.perform(as("acme", get("/api/v1/resource-requests/" + bobReq)))
        .andExpect(jsonPath("$.resourceAvailable").value(false))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  void databaseEnforcesOneDeploymentPerResource_F13() {
    jdbc.update(
        """
        INSERT INTO resource_request (resource_id, incident_id, requested_by, status, request_date,
          expected_return_date, deploy_date)
        VALUES (:r, :i, 'alice', 'DEPLOYED', CURRENT_DATE, CURRENT_DATE, CURRENT_DATE)
        """,
        Map.of("r", crane, "i", incident));
    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            jdbc.update(
                """
                INSERT INTO resource_request (resource_id, incident_id, requested_by, status,
                  request_date, expected_return_date, deploy_date)
                VALUES (:r, :i, 'alice', 'DEPLOYED', CURRENT_DATE, CURRENT_DATE, CURRENT_DATE)
                """,
                Map.of("r", crane, "i", incident)))
        .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
  }

  @Test
  void duplicateActiveRequestIs409ButHistoryDoesNotBlock_F14() throws Exception {
    long first = requestResource("alice", crane, incident, false);
    postJson(
            "alice",
            "/api/v1/resource-requests",
            """
            {"resourceId":%d,"incidentId":"%s","expectedReturnDate":"%s"}
            """
                .formatted(crane, incident, today()))
        .andExpect(status().isConflict());

    action("acme", first, "reject").andExpect(status().isOk());
    long second = requestResource("alice", crane, incident, false);
    action("alice", second, "cancel").andExpect(status().isOk());
    long third = requestResource("alice", crane, incident, false);
    action("acme", third, "deploy").andExpect(status().isOk());
    action("acme", third, "return").andExpect(status().isOk());
    requestResource("alice", crane, incident, false);

    assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM resource_request", Map.of(), Integer.class))
        .isEqualTo(4);
  }

  @Test
  void rejectWhileDeployedElsewhereKeepsResourceUnavailable_F15() throws Exception {
    createUser("bob");
    String bobsIncident = createIncident("bob", "FD", 33.7, -84.3);
    long deployed = requestResource("alice", crane, incident, false);
    long pending = requestResource("bob", crane, bobsIncident, false);
    action("acme", deployed, "deploy").andExpect(status().isOk());

    action("acme", pending, "reject")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.resourceAvailable").value(false));
    mvc.perform(as("alice", get("/api/v1/resources/" + crane)))
        .andExpect(jsonPath("$.available").value(false))
        .andExpect(jsonPath("$.nextAvailableDate").value(today().plusDays(7).toString()));
  }

  @Test
  void cancelKeepsTheRowAndIsRequesterOnly_F16() throws Exception {
    long id = requestResource("alice", crane, incident, false);

    action("acme", id, "cancel")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("auth.forbidden"));
    action("mallory", id, "cancel").andExpect(status().isNotFound());
    action("alice", id, "cancel")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    assertThat(dbStatus(id)).isEqualTo("CANCELLED");
  }

  @Test
  void receivedRequestsExcludeRejected_F17() throws Exception {
    long rejected = requestResource("alice", crane, incident, false);
    action("acme", rejected, "reject").andExpect(status().isOk());
    long pending = requestResource("alice", crane, incident, false);

    mvc.perform(as("acme", get("/api/v1/resource-requests?role=owner&status=PENDING")))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].requestId").value(pending));
    mvc.perform(as("acme", get("/api/v1/resource-requests?role=owner")))
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].requestId").value(pending));
  }

  @Test
  void inUseListIsKeyedOnRequestStatus_F18() throws Exception {
    String second = createIncident("alice", "ED", 33.7, -84.3);
    long first = requestResource("alice", crane, incident, false);
    action("acme", first, "deploy").andExpect(status().isOk());
    action("alice", first, "return").andExpect(status().isOk());
    long current = requestResource("alice", crane, second, false);
    action("acme", current, "deploy").andExpect(status().isOk());

    mvc.perform(as("alice", get("/api/v1/resource-requests?role=requester&status=DEPLOYED")))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].requestId").value(current))
        .andExpect(jsonPath("$[0].incidentId").value(second));
    mvc.perform(as("alice", get("/api/v1/resource-requests?role=requester&status=PENDING")))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void strangersCannotSeeOrActOnRequests_S3() throws Exception {
    long id = requestResource("alice", crane, incident, false);

    mvc.perform(as("mallory", get("/api/v1/resource-requests/" + id)))
        .andExpect(status().isNotFound());
    for (String act : new String[] {"deploy", "reject", "cancel", "return"}) {
      action("mallory", id, act).andExpect(status().isNotFound());
    }
    action("alice", id, "deploy").andExpect(status().isForbidden());
    action("alice", id, "reject").andExpect(status().isForbidden());
    mvc.perform(as("mallory", get("/api/v1/resource-requests?role=owner")))
        .andExpect(jsonPath("$.length()").value(0));
    postJson(
            "mallory",
            "/api/v1/resource-requests",
            """
            {"resourceId":%d,"incidentId":"%s","expectedReturnDate":"%s"}
            """
                .formatted(crane, incident, today()))
        .andExpect(status().isNotFound());
  }

  @Test
  void deployImmediatelyRules() throws Exception {
    String acmeIncident = createIncident("acme", "MD", 33.7, -84.3);
    postJson(
            "alice",
            "/api/v1/resource-requests",
            """
            {"resourceId":%d,"incidentId":"%s","expectedReturnDate":"%s","deployImmediately":true}
            """
                .formatted(crane, incident, today()))
        .andExpect(status().isForbidden());

    long own = requestResource("acme", crane, acmeIncident, true);
    mvc.perform(as("acme", get("/api/v1/resource-requests/" + own)))
        .andExpect(jsonPath("$.status").value("DEPLOYED"))
        .andExpect(jsonPath("$.deployDate").value(today().toString()));

    String another = createIncident("acme", "ED", 33.7, -84.3);
    postJson(
            "acme",
            "/api/v1/resource-requests",
            """
            {"resourceId":%d,"incidentId":"%s","expectedReturnDate":"%s","deployImmediately":true}
            """
                .formatted(crane, another, today()))
        .andExpect(status().isConflict());
  }

  @Test
  void expectedReturnDateMustNotBeInThePast() throws Exception {
    postJson(
            "alice",
            "/api/v1/resource-requests",
            """
            {"resourceId":%d,"incidentId":"%s","expectedReturnDate":"%s"}
            """
                .formatted(crane, incident, today().minusDays(1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("expectedReturnDate"));
    postJson(
            "alice",
            "/api/v1/resource-requests",
            """
            {"resourceId":999999,"incidentId":"%s","expectedReturnDate":"%s"}
            """
                .formatted(incident, today()))
        .andExpect(status().isNotFound());
  }
}
