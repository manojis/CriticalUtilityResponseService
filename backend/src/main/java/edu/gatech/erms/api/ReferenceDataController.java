package edu.gatech.erms.api;

import edu.gatech.erms.api.dto.DeclarationResponse;
import edu.gatech.erms.api.dto.EsfResponse;
import edu.gatech.erms.service.ReferenceDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ordered reference data for dropdowns. */
@RestController
@RequestMapping(ApiPaths.V1)
@Tag(name = "Reference data")
@ApiResponse(responseCode = "401", description = "auth.unauthorized")
public class ReferenceDataController {

  private final ReferenceDataService referenceData;

  public ReferenceDataController(ReferenceDataService referenceData) {
    this.referenceData = referenceData;
  }

  @GetMapping("/esfs")
  @Operation(summary = "All ESFs ordered by number")
  @ApiResponse(responseCode = "200", description = "OK")
  public List<EsfResponse> esfs() {
    return referenceData.esfs().stream().map(EsfResponse::from).toList();
  }

  @GetMapping("/cost-units")
  @Operation(summary = "All cost units, alphabetical")
  @ApiResponse(responseCode = "200", description = "OK")
  public List<String> costUnits() {
    return referenceData.costUnits();
  }

  @GetMapping("/declarations")
  @Operation(summary = "All declarations ordered by name")
  @ApiResponse(responseCode = "200", description = "OK")
  public List<DeclarationResponse> declarations() {
    return referenceData.declarations().stream().map(DeclarationResponse::from).toList();
  }
}
