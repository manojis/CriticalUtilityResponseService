package edu.gatech.erms.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import edu.gatech.erms.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** The OpenAPI document (served only when docs are enabled) covers every endpoint in API.md. */
@TestPropertySource(
    properties = {
      "erms.api-docs-enabled=true",
      "springdoc.api-docs.enabled=true",
      "springdoc.swagger-ui.enabled=true"
    })
class OpenApiIT extends AbstractIntegrationTest {

  @Test
  void openApiListsEveryDocumentedOperation() throws Exception {
    String body =
        mvc.perform(get("/api/openapi"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Map<String, Map<String, Object>> paths = JsonPath.read(body, "$.paths");

    assertThat(paths)
        .containsOnlyKeys(
            "/api/v1/auth/login",
            "/api/v1/me",
            "/api/v1/esfs",
            "/api/v1/cost-units",
            "/api/v1/declarations",
            "/api/v1/incidents",
            "/api/v1/incidents/{incidentId}",
            "/api/v1/resources",
            "/api/v1/resources/{resourceId}",
            "/api/v1/resource-requests",
            "/api/v1/resource-requests/{requestId}",
            "/api/v1/resource-requests/{requestId}/deploy",
            "/api/v1/resource-requests/{requestId}/reject",
            "/api/v1/resource-requests/{requestId}/cancel",
            "/api/v1/resource-requests/{requestId}/return",
            "/api/v1/reports/resources-by-esf");
    assertThat(paths.get("/api/v1/resource-requests")).containsOnlyKeys("get", "post");
    assertThat(paths.get("/api/v1/incidents")).containsOnlyKeys("get", "post");
    assertThat(
            (Map<String, Object>)
                JsonPath.read(body, "$.paths['/api/v1/resource-requests'].post.responses"))
        .containsKeys("201", "400", "401", "403", "404", "409");
    assertThat((Object) JsonPath.read(body, "$.components.securitySchemes.bearerAuth.scheme"))
        .isEqualTo("bearer");
  }

  @Test
  void swaggerUiIsReachableUnderApi() throws Exception {
    mvc.perform(get("/api/docs")).andExpect(status().is3xxRedirection());
    mvc.perform(get("/api/swagger-ui/index.html")).andExpect(status().isOk());
  }
}
