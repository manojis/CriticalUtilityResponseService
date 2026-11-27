package edu.gatech.erms.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.gatech.erms.exception.FieldValidationException;
import edu.gatech.erms.exception.NotFoundException;
import edu.gatech.erms.service.IncidentService;
import edu.gatech.erms.support.ErmsWebMvcTest;
import edu.gatech.erms.support.TestData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ErmsWebMvcTest(IncidentController.class)
class IncidentControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private IncidentService incidentService;

  @Test
  void createReturns201WithLocation() throws Exception {
    when(incidentService.create(eq("alice"), any())).thenReturn(TestData.incident("MD-3", "alice"));

    mvc.perform(
            post("/api/v1/incidents")
                .with(jwt().jwt(j -> j.subject("alice")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"declarationAbbreviation":"MD","incidentDate":"2026-11-20",
                     "description":"Flooding on I-75","latitude":33.7490,"longitude":-84.3880,
                     "incidentId":"MD-999","ownerUsername":"bob"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/incidents/MD-3"))
        .andExpect(jsonPath("$.incidentId").value("MD-3"))
        .andExpect(jsonPath("$.latitude").value(33.749))
        .andExpect(jsonPath("$.longitude").value(-84.388))
        .andExpect(jsonPath("$.ownerUsername").value("alice"));
  }

  @Test
  void validationErrorsListEveryField() throws Exception {
    mvc.perform(
            post("/api/v1/incidents")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"declarationAbbreviation":"MD","incidentDate":"2026-11-20",
                     "description":"x","latitude":95,"longitude":-184.38}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation.failed"))
        .andExpect(jsonPath("$.errors.length()").value(3))
        .andExpect(jsonPath("$.errors[0].field").value("description"))
        .andExpect(jsonPath("$.errors[0].message").value("size must be between 6 and 500"))
        .andExpect(jsonPath("$.errors[1].field").value("latitude"))
        .andExpect(jsonPath("$.errors[1].message").value("must be less than or equal to 90"))
        .andExpect(jsonPath("$.errors[2].field").value("longitude"));
    verifyNoInteractions(incidentService);
  }

  @Test
  void serviceFieldErrorsUseTheSameShape() throws Exception {
    when(incidentService.create(eq("alice"), any()))
        .thenThrow(FieldValidationException.of("incidentDate", "must not be after today"));

    mvc.perform(
            post("/api/v1/incidents")
                .with(jwt().jwt(j -> j.subject("alice")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"declarationAbbreviation":"MD","incidentDate":"2099-01-01",
                     "description":"Flooding on I-75","latitude":1,"longitude":1}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation.failed"))
        .andExpect(jsonPath("$.errors[0].field").value("incidentDate"));
  }

  @Test
  void invalidDateFormatIsMalformed_F9() throws Exception {
    mvc.perform(
            post("/api/v1/incidents")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"declarationAbbreviation":"MD","incidentDate":"11-20-2026",
                     "description":"Flooding on I-75","latitude":1,"longitude":1}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("request.malformed"));
  }

  @Test
  void listAndGet() throws Exception {
    when(incidentService.listOwned("alice"))
        .thenReturn(List.of(TestData.incident("MD-1", "alice")));
    when(incidentService.getOwned("alice", "MD-9"))
        .thenThrow(NotFoundException.of("Incident", "MD-9"));

    mvc.perform(get("/api/v1/incidents").with(jwt().jwt(j -> j.subject("alice"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].incidentId").value("MD-1"))
        .andExpect(jsonPath("$[0].incidentDate").value("2026-11-24"));
    mvc.perform(get("/api/v1/incidents/MD-9").with(jwt().jwt(j -> j.subject("alice"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Incident MD-9 not found"));
  }
}
