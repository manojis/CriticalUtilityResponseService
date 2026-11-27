package edu.gatech.erms.it;

import static org.assertj.core.api.Assertions.assertThat;

import edu.gatech.erms.support.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Real servers: health/metrics on the management port only; API docs off by default. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ActuatorIT {

  @LocalServerPort private int appPort;
  @LocalManagementPort private int managementPort;

  private final HttpClient http = HttpClient.newHttpClient();

  private HttpResponse<String> get(int port, String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void probesAreUpOnManagementPort() throws Exception {
    assertThat(managementPort).isNotEqualTo(appPort);
    HttpResponse<String> readiness = get(managementPort, "/actuator/health/readiness");
    assertThat(readiness.statusCode()).isEqualTo(200);
    assertThat(readiness.body()).contains("\"status\":\"UP\"");
    assertThat(get(managementPort, "/actuator/health/liveness").statusCode()).isEqualTo(200);
    assertThat(get(managementPort, "/actuator/health").body()).doesNotContain("components");
  }

  @Test
  void prometheusMetricsAreExposed() throws Exception {
    HttpResponse<String> metrics = get(managementPort, "/actuator/prometheus");
    assertThat(metrics.statusCode()).isEqualTo(200);
    assertThat(metrics.body()).contains("jvm_memory_used_bytes").contains("jdbc_connections_max");
  }

  @Test
  void actuatorIsNotServedOnAppPort() throws Exception {
    assertThat(get(appPort, "/actuator/health").statusCode()).isIn(401, 404);
  }

  @Test
  void apiDocsAreDisabledByDefault() throws Exception {
    assertThat(get(appPort, "/api/openapi").statusCode()).isEqualTo(401);
  }

  @Test
  void requestIdIsEchoed() throws Exception {
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + appPort + "/api/v1/me"))
                .header("X-Request-Id", "trace-42")
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue("X-Request-Id")).hasValue("trace-42");
    assertThat(response.body()).contains("\"requestId\":\"trace-42\"");
  }
}
