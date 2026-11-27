package edu.gatech.erms.api;

import edu.gatech.erms.api.dto.CreateIncidentRequest;
import edu.gatech.erms.api.dto.IncidentResponse;
import edu.gatech.erms.domain.Incident;
import edu.gatech.erms.security.CurrentUser;
import edu.gatech.erms.service.IncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Incidents owned by the caller. */
@RestController
@RequestMapping(ApiPaths.V1 + "/incidents")
@Tag(name = "Incidents")
@ApiResponse(responseCode = "401", description = "auth.unauthorized")
public class IncidentController {

  private final IncidentService incidentService;

  public IncidentController(IncidentService incidentService) {
    this.incidentService = incidentService;
  }

  @PostMapping
  @Operation(summary = "Declare an incident (server-generated id <ABBR>-<n>)")
  @ApiResponse(responseCode = "201", description = "Created")
  @ApiResponse(responseCode = "400", description = "validation.failed / request.malformed")
  public ResponseEntity<IncidentResponse> create(
      @CurrentUser String username, @Valid @RequestBody CreateIncidentRequest request) {
    Incident incident = incidentService.create(username, request.toCommand());
    return ResponseEntity.created(URI.create(ApiPaths.V1 + "/incidents/" + incident.incidentId()))
        .body(IncidentResponse.from(incident));
  }

  @GetMapping
  @Operation(summary = "The caller's incidents, newest first")
  @ApiResponse(responseCode = "200", description = "OK")
  public List<IncidentResponse> list(@CurrentUser String username) {
    return incidentService.listOwned(username).stream().map(IncidentResponse::from).toList();
  }

  @GetMapping("/{incidentId}")
  @Operation(summary = "One of the caller's incidents")
  @ApiResponse(responseCode = "200", description = "OK")
  @ApiResponse(responseCode = "404", description = "resource.not_found (missing or not owned)")
  public IncidentResponse get(
      @CurrentUser String username, @PathVariable @Size(max = 16) String incidentId) {
    return IncidentResponse.from(incidentService.getOwned(username, incidentId));
  }
}
