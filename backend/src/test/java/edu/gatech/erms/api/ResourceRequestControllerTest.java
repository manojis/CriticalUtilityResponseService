package edu.gatech.erms.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.gatech.erms.domain.RequestRole;
import edu.gatech.erms.domain.RequestStatus;
import edu.gatech.erms.exception.ConflictException;
import edu.gatech.erms.exception.ForbiddenException;
import edu.gatech.erms.service.CreateResourceRequestCommand;
import edu.gatech.erms.service.ResourceRequestService;
import edu.gatech.erms.support.ErmsWebMvcTest;
import edu.gatech.erms.support.TestData;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ErmsWebMvcTest(ResourceRequestController.class)
class ResourceRequestControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private ResourceRequestService requestService;

  @Test
  void createReturns201AndIgnoresClientSuppliedRequester_S3() throws Exception {
    when(requestService.create(eq("alice"), any()))
        .thenReturn(TestData.request(7, RequestStatus.PENDING, "alice", "acme"));

    mvc.perform(
            post("/api/v1/resource-requests")
                .with(jwt().jwt(j -> j.subject("alice")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"resourceId":1001,"incidentId":"MD-1","expectedReturnDate":"2026-12-10",
                     "requestedBy_username":"bob","status":"DEPLOYED"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/resource-requests/7"))
        .andExpect(jsonPath("$.requestId").value(7))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.requestedBy").value("alice"))
        .andExpect(jsonPath("$.requestDate").value("2026-11-25"))
        .andExpect(jsonPath("$.resourceAvailable").value(true))
        .andExpect(jsonPath("$.deployDate").doesNotExist());
    verify(requestService)
        .create(
            "alice",
            new CreateResourceRequestCommand(1001L, "MD-1", LocalDate.of(2026, 12, 10), false));
  }

  @Test
  void createValidation() throws Exception {
    mvc.perform(
            post("/api/v1/resource-requests")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resourceId\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.field=='resourceId')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field=='incidentId')]").exists())
        .andExpect(jsonPath("$.errors[?(@.field=='expectedReturnDate')]").exists());
  }

  @Test
  void listParsesRoleAndStatuses() throws Exception {
    when(requestService.list(
            "acme", RequestRole.OWNER, List.of(RequestStatus.PENDING, RequestStatus.DEPLOYED)))
        .thenReturn(List.of(TestData.request(7, RequestStatus.DEPLOYED, "alice", "acme")));

    mvc.perform(
            get("/api/v1/resource-requests")
                .param("role", "owner")
                .param("status", "PENDING,DEPLOYED")
                .with(jwt().jwt(j -> j.subject("acme"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("DEPLOYED"))
        .andExpect(jsonPath("$[0].deployDate").value("2026-11-25"))
        .andExpect(jsonPath("$[0].resourceAvailable").value(false));
  }

  @Test
  void listWithoutStatusMeansAll() throws Exception {
    when(requestService.list("alice", RequestRole.REQUESTER, List.of())).thenReturn(List.of());
    mvc.perform(
            get("/api/v1/resource-requests")
                .param("role", "requester")
                .with(jwt().jwt(j -> j.subject("alice"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void listRejectsBadRoleAndStatus() throws Exception {
    mvc.perform(get("/api/v1/resource-requests").with(jwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("request.malformed"));
    mvc.perform(get("/api/v1/resource-requests").param("role", "admin").with(jwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation.failed"))
        .andExpect(jsonPath("$.errors[0].field").value("role"));
    mvc.perform(
            get("/api/v1/resource-requests")
                .param("role", "owner")
                .param("status", "LOST")
                .with(jwt()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("request.malformed"));
  }

  @Test
  void actionsCallTheMatchingServiceMethod() throws Exception {
    when(requestService.deploy("acme", 7))
        .thenReturn(TestData.request(7, RequestStatus.DEPLOYED, "alice", "acme"));
    when(requestService.reject("acme", 8))
        .thenReturn(TestData.request(8, RequestStatus.REJECTED, "alice", "acme"));
    when(requestService.cancel("acme", 9))
        .thenReturn(TestData.request(9, RequestStatus.CANCELLED, "acme", "x"));
    when(requestService.returnResource("acme", 10))
        .thenReturn(TestData.request(10, RequestStatus.RETURNED, "alice", "acme"));
    when(requestService.get("acme", 11))
        .thenReturn(TestData.request(11, RequestStatus.PENDING, "alice", "acme"));

    var acme = jwt().jwt(j -> j.subject("acme"));
    mvc.perform(post("/api/v1/resource-requests/7/deploy").with(acme))
        .andExpect(jsonPath("$.status").value("DEPLOYED"));
    mvc.perform(post("/api/v1/resource-requests/8/reject").with(acme))
        .andExpect(jsonPath("$.status").value("REJECTED"));
    mvc.perform(post("/api/v1/resource-requests/9/cancel").with(acme))
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    mvc.perform(post("/api/v1/resource-requests/10/return").with(acme))
        .andExpect(jsonPath("$.status").value("RETURNED"))
        .andExpect(jsonPath("$.actualReturnDate").value("2026-11-25"));
    mvc.perform(get("/api/v1/resource-requests/11").with(acme))
        .andExpect(jsonPath("$.requestId").value(11));
  }

  @Test
  void forbiddenAndConflictMapToProblemDetails() throws Exception {
    when(requestService.deploy("alice", 7))
        .thenThrow(new ForbiddenException("Only the resource owner can deploy request 7"));
    when(requestService.cancel("alice", 7))
        .thenThrow(new ConflictException("Request 7 is DEPLOYED; cannot cancel"));

    var alice = jwt().jwt(j -> j.subject("alice"));
    mvc.perform(post("/api/v1/resource-requests/7/deploy").with(alice))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("auth.forbidden"));
    mvc.perform(post("/api/v1/resource-requests/7/cancel").with(alice))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("state.conflict"))
        .andExpect(jsonPath("$.detail").value("Request 7 is DEPLOYED; cannot cancel"));
  }
}
