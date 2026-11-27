package edu.gatech.erms.api;

import edu.gatech.erms.api.dto.EsfReportResponse;
import edu.gatech.erms.security.CurrentUser;
import edu.gatech.erms.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reports over the caller's resources. */
@RestController
@RequestMapping(ApiPaths.V1 + "/reports")
@Tag(name = "Reports")
public class ReportController {

  private final ReportService reportService;

  public ReportController(ReportService reportService) {
    this.reportService = reportService;
  }

  @GetMapping("/resources-by-esf")
  @Operation(summary = "The caller's resources grouped by primary ESF (all 15 ESFs) with totals")
  @ApiResponse(responseCode = "200", description = "OK")
  @ApiResponse(responseCode = "401", description = "auth.unauthorized")
  public EsfReportResponse resourcesByEsf(@CurrentUser String username) {
    return EsfReportResponse.from(reportService.resourcesByEsf(username));
  }
}
