package edu.gatech.erms.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.gatech.erms.service.AuthService;
import edu.gatech.erms.support.ErmsWebMvcTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Legacy defect S4: no wildcard CORS; an explicit allow-list only, off by default. */
class CorsTest {

  @Nested
  @ErmsWebMvcTest(value = AuthController.class)
  class DisabledByDefault {

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService authService;

    @Test
    void noCorsHeadersForAnyOrigin_S4() throws Exception {
      mvc.perform(
              options("/api/v1/auth/login")
                  .header("Origin", "http://evil.example")
                  .header("Access-Control-Request-Method", "POST"))
          .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
  }

  @Nested
  @ErmsWebMvcTest(
      value = AuthController.class,
      properties = "erms.cors.allowed-origins=http://localhost:5173")
  class AllowList {

    @Autowired private MockMvc mvc;
    @MockitoBean private AuthService authService;

    @Test
    void allowedOriginGetsCorsHeaders_S4() throws Exception {
      mvc.perform(
              options("/api/v1/auth/login")
                  .header("Origin", "http://localhost:5173")
                  .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isOk())
          .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void otherOriginIsRejected_S4() throws Exception {
      mvc.perform(
              options("/api/v1/auth/login")
                  .header("Origin", "http://evil.example")
                  .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isForbidden())
          .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
  }
}
