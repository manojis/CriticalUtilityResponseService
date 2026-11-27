package edu.gatech.erms.api;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.gatech.erms.domain.Declaration;
import edu.gatech.erms.domain.Esf;
import edu.gatech.erms.domain.EsfReportRow;
import edu.gatech.erms.service.ReferenceDataService;
import edu.gatech.erms.service.ReportService;
import edu.gatech.erms.support.ErmsWebMvcTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ErmsWebMvcTest({ReferenceDataController.class, ReportController.class})
class ReferenceDataAndReportControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private ReferenceDataService referenceData;
  @MockitoBean private ReportService reportService;

  @Test
  void referenceDataAreOrderedArrays_F23() throws Exception {
    when(referenceData.esfs())
        .thenReturn(List.of(new Esf(1, "Transportation"), new Esf(2, "Communications")));
    when(referenceData.costUnits()).thenReturn(List.of("Day", "Each", "Hour", "Week"));
    when(referenceData.declarations()).thenReturn(List.of(new Declaration("ED", "Emergency")));

    mvc.perform(get("/api/v1/esfs").with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].esfNumber").value(1))
        .andExpect(jsonPath("$[1].description").value("Communications"));
    mvc.perform(get("/api/v1/cost-units").with(jwt()))
        .andExpect(jsonPath("$[0]").value("Day"))
        .andExpect(jsonPath("$[3]").value("Week"));
    mvc.perform(get("/api/v1/declarations").with(jwt()))
        .andExpect(jsonPath("$[0].abbreviation").value("ED"))
        .andExpect(jsonPath("$[0].name").value("Emergency"));
  }

  @Test
  void referenceDataRequireAuthentication() throws Exception {
    mvc.perform(get("/api/v1/esfs")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/reports/resources-by-esf")).andExpect(status().isUnauthorized());
  }

  @Test
  void reportHasRowsAndTotals_F11() throws Exception {
    when(reportService.resourcesByEsf("acme"))
        .thenReturn(
            new ReportService.EsfReport(
                List.of(new EsfReportRow(1, "Transportation", 2, 1)), 2, 1));

    mvc.perform(get("/api/v1/reports/resources-by-esf").with(jwt().jwt(j -> j.subject("acme"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rows[0].esfNumber").value(1))
        .andExpect(jsonPath("$.rows[0].esfDescription").value("Transportation"))
        .andExpect(jsonPath("$.rows[0].totalResources").value(2))
        .andExpect(jsonPath("$.rows[0].resourcesInUse").value(1))
        .andExpect(jsonPath("$.totals.totalResources").value(2))
        .andExpect(jsonPath("$.totals.resourcesInUse").value(1));
  }
}
