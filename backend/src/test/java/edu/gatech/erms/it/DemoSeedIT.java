package edu.gatech.erms.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import edu.gatech.erms.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mysql.MySQLContainer;

/**
 * The {@code demo} profile applies the idempotent seed (own database, so the seed never leaks into
 * other integration tests). Mirrors scripts/smoke-test.sh.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
class DemoSeedIT {

  @Autowired private MockMvc mvc;

  @TestConfiguration(proxyBeanMethods = false)
  static class DedicatedDatabase {
    @Bean
    @ServiceConnection
    MySQLContainer demoMysql() {
      return new MySQLContainer(TestcontainersConfiguration.MYSQL_IMAGE);
    }
  }

  private String login(String user) throws Exception {
    String body =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + user + "\",\"password\":\"password\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + JsonPath.read(body, "$.accessToken");
  }

  @Test
  void demoUsersCanLogInWithPassword() throws Exception {
    for (String user : new String[] {"alice", "metro_city", "fema_atl", "acme_rescue"}) {
      mvc.perform(get("/api/v1/me").header("Authorization", login(user)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.username").value(user));
    }
  }

  @Test
  void seedHasResourcesIncidentAndAdvancedSequence() throws Exception {
    String alice = login("alice");
    mvc.perform(get("/api/v1/esfs").header("Authorization", alice))
        .andExpect(jsonPath("$.length()").value(15));
    mvc.perform(get("/api/v1/resources?incidentId=MD-1").header("Authorization", alice))
        .andExpect(jsonPath("$.totalElements").value(6))
        .andExpect(jsonPath("$.content[0].distanceKm").value(0.0));
    mvc.perform(get("/api/v1/resources/1001").header("Authorization", alice))
        .andExpect(jsonPath("$.capabilities.length()").value(2));
    mvc.perform(
            post("/api/v1/incidents")
                .header("Authorization", alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"declarationAbbreviation":"MD","incidentDate":"2026-01-01",
                     "description":"After the seed","latitude":1,"longitude":1}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.incidentId").value("MD-2"));
  }
}
