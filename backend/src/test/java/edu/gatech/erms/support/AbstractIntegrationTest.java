package edu.gatech.erms.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import edu.gatech.erms.domain.UserAccount;
import edu.gatech.erms.domain.UserType;
import edu.gatech.erms.security.JwtTokenService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base class for full-stack integration tests: real Spring context, real security, Flyway-migrated
 * MySQL 26.10 (Testcontainers). Every test starts from an empty dataset (reference data kept).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  @Autowired protected MockMvc mvc;
  @Autowired protected NamedParameterJdbcTemplate jdbc;
  @Autowired protected JwtTokenService tokens;
  @Autowired protected Clock clock;

  @BeforeEach
  void cleanDatabase() {
    for (String table :
        List.of(
            "resource_request",
            "resource_capability",
            "additional_esf",
            "resource",
            "incident",
            "individual",
            "municipality",
            "government_agency",
            "company",
            "app_user")) {
      jdbc.update("DELETE FROM " + table, Map.of());
    }
    jdbc.update("UPDATE incident_sequence SET next_value = 1", Map.of());
  }

  protected LocalDate today() {
    return LocalDate.now(clock);
  }

  /** Creates a user (password "password") of the given type with its subtype row. */
  protected void createUser(String username, UserType type) {
    jdbc.update(
        "INSERT INTO app_user (username, name, password_hash, user_type) VALUES (:u, :n, :h, :t)",
        Map.of(
            "u",
            username,
            "n",
            "Name of " + username,
            "h",
            TestData.PASSWORD_HASH,
            "t",
            type.name()));
    String subtype =
        switch (type) {
          case INDIVIDUAL ->
              "INSERT INTO individual (username, job_title, hire_date) VALUES (:u, 'Coordinator', '2015-03-01')";
          case MUNICIPALITY ->
              "INSERT INTO municipality (username, municipality_category) VALUES (:u, 'City')";
          case GOVERNMENT_AGENCY ->
              "INSERT INTO government_agency (username, agency_name_local_office) VALUES (:u, 'FEMA')";
          case COMPANY ->
              "INSERT INTO company (username, headquarters, num_employees) VALUES (:u, 'Atlanta, GA', 250)";
        };
    jdbc.update(subtype, Map.of("u", username));
  }

  protected void createUser(String username) {
    createUser(username, UserType.INDIVIDUAL);
  }

  protected String bearer(String username) {
    return "Bearer "
        + tokens
            .issue(new UserAccount(username, "Name of " + username, "n/a", UserType.INDIVIDUAL))
            .accessToken();
  }

  protected MockHttpServletRequestBuilder as(
      String username, MockHttpServletRequestBuilder request) {
    return request.header(HttpHeaders.AUTHORIZATION, bearer(username));
  }

  protected ResultActions postJson(String username, String path, String body) throws Exception {
    return mvc.perform(
        as(username, post(path)).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  protected static <T> T read(MvcResult result, String path) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), path);
  }

  protected String createIncident(String owner, String abbr, double lat, double lon)
      throws Exception {
    MvcResult result =
        postJson(
                owner,
                "/api/v1/incidents",
                """
                {"declarationAbbreviation":"%s","incidentDate":"%s","description":"Incident near %s",
                 "latitude":%s,"longitude":%s}
                """
                    .formatted(abbr, today(), owner, lat, lon))
            .andExpect(status().isCreated())
            .andReturn();
    return read(result, "$.incidentId");
  }

  /** Creates a resource via the API and returns its id. {@code maxDistanceKm} may be null. */
  protected long createResource(
      String owner,
      String name,
      String model,
      int primaryEsf,
      List<Integer> additionalEsfs,
      List<String> capabilities,
      double lat,
      double lon,
      Double maxDistanceKm)
      throws Exception {
    String caps =
        capabilities.stream().map(c -> "\"" + c + "\"").reduce((a, b) -> a + "," + b).orElse("");
    String esfs =
        additionalEsfs.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
    MvcResult result =
        postJson(
                owner,
                "/api/v1/resources",
                """
                {"resourceName":"%s","model":%s,"primaryEsf":%d,"additionalEsfs":[%s],
                 "capabilities":[%s],"cost":10.00,"costUnit":"Hour","latitude":%s,"longitude":%s,
                 "maxDistanceKm":%s}
                """
                    .formatted(
                        name,
                        model == null ? "null" : "\"" + model + "\"",
                        primaryEsf,
                        esfs,
                        caps,
                        lat,
                        lon,
                        maxDistanceKm == null ? "null" : maxDistanceKm.toString()))
            .andExpect(status().isCreated())
            .andReturn();
    return ((Number) read(result, "$.resourceId")).longValue();
  }

  protected long createResource(String owner, String name, double lat, double lon)
      throws Exception {
    return createResource(owner, name, null, 1, List.of(), List.of(), lat, lon, null);
  }

  /** Creates a PENDING (or DEPLOYED when {@code deployNow}) request and returns its id. */
  protected long requestResource(
      String requester, long resourceId, String incidentId, boolean deployNow) throws Exception {
    MvcResult result =
        postJson(
                requester,
                "/api/v1/resource-requests",
                """
                {"resourceId":%d,"incidentId":"%s","expectedReturnDate":"%s","deployImmediately":%s}
                """
                    .formatted(resourceId, incidentId, today().plusDays(7), deployNow))
            .andExpect(status().isCreated())
            .andReturn();
    return ((Number) read(result, "$.requestId")).longValue();
  }

  protected ResultActions action(String username, long requestId, String action) throws Exception {
    return mvc.perform(as(username, post("/api/v1/resource-requests/" + requestId + "/" + action)));
  }
}
