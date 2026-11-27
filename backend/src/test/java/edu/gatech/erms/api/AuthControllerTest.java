package edu.gatech.erms.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.gatech.erms.domain.UserAccount;
import edu.gatech.erms.domain.UserType;
import edu.gatech.erms.exception.InvalidCredentialsException;
import edu.gatech.erms.security.IssuedToken;
import edu.gatech.erms.service.AuthService;
import edu.gatech.erms.support.ErmsWebMvcTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ErmsWebMvcTest(AuthController.class)
class AuthControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private AuthService authService;

  @Test
  void loginReturnsTokenAndUser() throws Exception {
    when(authService.login("alice", "password"))
        .thenReturn(
            new AuthService.LoginResult(
                new IssuedToken("jwt-token", 3600),
                new UserAccount("alice", "Alice Smith", "hash", UserType.INDIVIDUAL)));

    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"password\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-Id", matchesPattern(".+")))
        .andExpect(jsonPath("$.accessToken").value("jwt-token"))
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresIn").value(3600))
        .andExpect(jsonPath("$.user.username").value("alice"))
        .andExpect(jsonPath("$.user.name").value("Alice Smith"))
        .andExpect(jsonPath("$.user.userType").value("INDIVIDUAL"))
        .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
  }

  @Test
  void badCredentialsAre401ProblemDetail_S7() throws Exception {
    when(authService.login("alice", "nope")).thenThrow(new InvalidCredentialsException());

    mvc.perform(
            post("/api/v1/auth/login")
                .header("X-Request-Id", "req-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"nope\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.title").value("Unauthorized"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.detail").value("Invalid username or password"))
        .andExpect(jsonPath("$.instance").value("/api/v1/auth/login"))
        .andExpect(jsonPath("$.code").value("auth.invalid_credentials"))
        .andExpect(jsonPath("$.requestId").value("req-123"))
        .andExpect(jsonPath("$.success").doesNotExist());
  }

  @Test
  void blankFieldsAreValidationErrors() throws Exception {
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"unknownField\":true}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation.failed"))
        .andExpect(jsonPath("$.detail").value("Request validation failed"))
        .andExpect(jsonPath("$.errors[?(@.field=='username')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists());
  }

  @Test
  void malformedJsonIsRequestMalformed() throws Exception {
    mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("request.malformed"));
  }
}
