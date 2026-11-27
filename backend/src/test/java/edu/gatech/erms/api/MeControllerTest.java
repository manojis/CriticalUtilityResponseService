package edu.gatech.erms.api;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.gatech.erms.domain.UserProfile;
import edu.gatech.erms.domain.UserType;
import edu.gatech.erms.exception.NotFoundException;
import edu.gatech.erms.service.UserService;
import edu.gatech.erms.support.ErmsWebMvcTest;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ErmsWebMvcTest(MeController.class)
class MeControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private UserService userService;

  @Test
  void missingTokenIs401ProblemDetail() throws Exception {
    mvc.perform(get("/api/v1/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(jsonPath("$.code").value("auth.unauthorized"))
        .andExpect(jsonPath("$.requestId").exists())
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  void invalidTokenIs401() throws Exception {
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("auth.unauthorized"));
  }

  @Test
  void identityComesFromTokenSubjectAndNullsAreOmitted_S3() throws Exception {
    when(userService.getProfile("alice"))
        .thenReturn(
            new UserProfile(
                "alice",
                "Alice Smith",
                UserType.INDIVIDUAL,
                "Emergency Coordinator",
                LocalDate.of(2015, 3, 1),
                "ignored",
                null,
                null,
                null));

    mvc.perform(get("/api/v1/me").param("username", "bob").with(jwt().jwt(j -> j.subject("alice"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"))
        .andExpect(jsonPath("$.jobTitle").value("Emergency Coordinator"))
        .andExpect(jsonPath("$.hireDate").value("2015-03-01"))
        .andExpect(jsonPath("$.municipalityCategory").doesNotExist())
        .andExpect(jsonPath("$.headquarters").doesNotExist());
  }

  @Test
  void companyProfileShowsCompanyFields() throws Exception {
    when(userService.getProfile("acme"))
        .thenReturn(
            new UserProfile(
                "acme", "Acme", UserType.COMPANY, null, null, null, null, "Atlanta, GA", 250L));

    mvc.perform(get("/api/v1/me").with(jwt().jwt(j -> j.subject("acme"))))
        .andExpect(jsonPath("$.headquarters").value("Atlanta, GA"))
        .andExpect(jsonPath("$.numberOfEmployees").value(250))
        .andExpect(jsonPath("$.jobTitle").doesNotExist());
  }

  @Test
  void unknownUserIs404NotA204_F19() throws Exception {
    when(userService.getProfile("ghost")).thenThrow(NotFoundException.of("User", "ghost"));

    mvc.perform(get("/api/v1/me").with(jwt().jwt(j -> j.subject("ghost"))))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("resource.not_found"));
  }
}
