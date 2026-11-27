package edu.gatech.erms.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import edu.gatech.erms.domain.UserType;
import edu.gatech.erms.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthIT extends AbstractIntegrationTest {

  private MvcResult login(String username, String password, int expectedStatus) throws Exception {
    return mvc.perform(
            post("/api/v1/auth/login")
                .header("X-Request-Id", "fixed-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
        .andExpect(status().is(expectedStatus))
        .andReturn();
  }

  @Test
  void loginWithBcryptPasswordThenUseTokenForMe_S2() throws Exception {
    createUser("acme_rescue", UserType.COMPANY);

    MvcResult result = login("acme_rescue", "password", 200);
    String token = read(result, "$.accessToken");

    assertThat((Integer) read(result, "$.expiresIn")).isEqualTo(3600);
    assertThat((String) read(result, "$.user.userType")).isEqualTo("COMPANY");
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("acme_rescue"))
        .andExpect(jsonPath("$.headquarters").value("Atlanta, GA"))
        .andExpect(jsonPath("$.numberOfEmployees").value(250));
  }

  @Test
  void passwordIsNeverStoredOrComparedInPlaintext_S2() {
    createUser("alice");
    String stored =
        jdbc.queryForObject(
            "SELECT password_hash FROM app_user WHERE username = 'alice'", Map.of(), String.class);
    assertThat(stored).startsWith("$2a$10$").isNotEqualTo("password");
  }

  @Test
  void wrongPasswordAndUnknownUserAreIndistinguishable_S7() throws Exception {
    createUser("alice");

    String wrongPassword = login("alice", "wrong", 401).getResponse().getContentAsString();
    String unknownUser = login("nobody", "wrong", 401).getResponse().getContentAsString();

    assertThat(wrongPassword).isEqualTo(unknownUser);
    assertThat((String) JsonPath.read(wrongPassword, "$.code"))
        .isEqualTo("auth.invalid_credentials");
  }

  @Test
  void deletedUserWithValidTokenGets404_F19() throws Exception {
    String token = bearer("ghost");
    mvc.perform(get("/api/v1/me").header("Authorization", token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("resource.not_found"));
  }

  @Test
  void everyApiCallWithoutTokenIs401_S3() throws Exception {
    for (String path :
        new String[] {
          "/api/v1/me",
          "/api/v1/esfs",
          "/api/v1/cost-units",
          "/api/v1/declarations",
          "/api/v1/incidents",
          "/api/v1/resources",
          "/api/v1/resource-requests?role=owner",
          "/api/v1/reports/resources-by-esf",
          "/api/openapi"
        }) {
      mvc.perform(get(path))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("auth.unauthorized"));
    }
  }

  @Test
  void referenceDataEndpoints() throws Exception {
    mvc.perform(as("alice", get("/api/v1/esfs")))
        .andExpect(jsonPath("$.length()").value(15))
        .andExpect(jsonPath("$[0].esfNumber").value(1))
        .andExpect(jsonPath("$[14].description").value("External Affairs"));
    mvc.perform(as("alice", get("/api/v1/cost-units")))
        .andExpect(jsonPath("$[0]").value("Day"))
        .andExpect(jsonPath("$[1]").value("Each"))
        .andExpect(jsonPath("$[2]").value("Hour"))
        .andExpect(jsonPath("$[3]").value("Week"));
    mvc.perform(as("alice", get("/api/v1/declarations")))
        .andExpect(jsonPath("$[0].name").value("Emergency"))
        .andExpect(jsonPath("$[3].abbreviation").value("MD"));
  }
}
