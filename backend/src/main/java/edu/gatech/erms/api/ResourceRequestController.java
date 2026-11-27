package edu.gatech.erms.api;

import edu.gatech.erms.api.dto.CreateResourceRequestRequest;
import edu.gatech.erms.api.dto.ResourceRequestResponse;
import edu.gatech.erms.domain.RequestRole;
import edu.gatech.erms.domain.RequestStatus;
import edu.gatech.erms.domain.ResourceRequest;
import edu.gatech.erms.exception.FieldValidationException;
import edu.gatech.erms.security.CurrentUser;
import edu.gatech.erms.service.ResourceRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The request → deploy → return workflow. */
@RestController
@RequestMapping(ApiPaths.V1 + "/resource-requests")
@Tag(name = "Resource requests")
@ApiResponse(responseCode = "401", description = "auth.unauthorized")
public class ResourceRequestController {

  private final ResourceRequestService requestService;

  public ResourceRequestController(ResourceRequestService requestService) {
    this.requestService = requestService;
  }

  @PostMapping
  @Operation(summary = "Request a resource for one of the caller's incidents (or deploy own)")
  @ApiResponse(responseCode = "201", description = "Created (PENDING, or DEPLOYED)")
  @ApiResponse(responseCode = "400", description = "validation.failed / request.malformed")
  @ApiResponse(
      responseCode = "403",
      description = "auth.forbidden (deployImmediately for a resource the caller does not own)")
  @ApiResponse(responseCode = "404", description = "resource.not_found (incident or resource)")
  @ApiResponse(
      responseCode = "409",
      description = "state.conflict (active request exists, or resource already deployed)")
  public ResponseEntity<ResourceRequestResponse> create(
      @CurrentUser String username, @Valid @RequestBody CreateResourceRequestRequest request) {
    ResourceRequest created = requestService.create(username, request.toCommand());
    return ResponseEntity.created(
            URI.create(ApiPaths.V1 + "/resource-requests/" + created.requestId()))
        .body(ResourceRequestResponse.from(created));
  }

  @GetMapping
  @Operation(summary = "Requests made by the caller (requester) or for the caller's resources")
  @ApiResponse(responseCode = "200", description = "OK, newest first")
  @ApiResponse(responseCode = "400", description = "validation.failed / request.malformed")
  public List<ResourceRequestResponse> list(
      @CurrentUser String username,
      @Parameter(schema = @Schema(allowableValues = {"requester", "owner"})) @RequestParam
          String role,
      @Parameter(description = "Comma-separated statuses; default all")
          @RequestParam(required = false)
          List<RequestStatus> status) {
    RequestRole requestRole = parseRole(role);
    List<RequestStatus> statuses =
        status == null ? List.of() : status.stream().filter(Objects::nonNull).toList();
    return requestService.list(username, requestRole, statuses).stream()
        .map(ResourceRequestResponse::from)
        .toList();
  }

  @GetMapping("/{requestId}")
  @Operation(summary = "One request (requester or resource owner only)")
  @ApiResponse(responseCode = "200", description = "OK")
  @ApiResponse(responseCode = "404", description = "resource.not_found (missing or not visible)")
  public ResourceRequestResponse get(@CurrentUser String username, @PathVariable long requestId) {
    return ResourceRequestResponse.from(requestService.get(username, requestId));
  }

  @PostMapping("/{requestId}/deploy")
  @Operation(summary = "Owner deploys a PENDING request")
  @ApiResponse(responseCode = "200", description = "DEPLOYED")
  @ApiResponse(responseCode = "403", description = "auth.forbidden (not the resource owner)")
  @ApiResponse(responseCode = "404", description = "resource.not_found")
  @ApiResponse(
      responseCode = "409",
      description = "state.conflict (not PENDING, or resource deployed)")
  public ResourceRequestResponse deploy(
      @CurrentUser String username, @PathVariable long requestId) {
    return ResourceRequestResponse.from(requestService.deploy(username, requestId));
  }

  @PostMapping("/{requestId}/reject")
  @Operation(summary = "Owner rejects a PENDING request")
  @ApiResponse(responseCode = "200", description = "REJECTED")
  @ApiResponse(responseCode = "403", description = "auth.forbidden (not the resource owner)")
  @ApiResponse(responseCode = "404", description = "resource.not_found")
  @ApiResponse(responseCode = "409", description = "state.conflict (not PENDING)")
  public ResourceRequestResponse reject(
      @CurrentUser String username, @PathVariable long requestId) {
    return ResourceRequestResponse.from(requestService.reject(username, requestId));
  }

  @PostMapping("/{requestId}/cancel")
  @Operation(summary = "Requester cancels a PENDING request")
  @ApiResponse(responseCode = "200", description = "CANCELLED")
  @ApiResponse(responseCode = "403", description = "auth.forbidden (not the requester)")
  @ApiResponse(responseCode = "404", description = "resource.not_found")
  @ApiResponse(responseCode = "409", description = "state.conflict (not PENDING)")
  public ResourceRequestResponse cancel(
      @CurrentUser String username, @PathVariable long requestId) {
    return ResourceRequestResponse.from(requestService.cancel(username, requestId));
  }

  @PostMapping("/{requestId}/return")
  @Operation(summary = "Requester or owner returns a DEPLOYED resource")
  @ApiResponse(responseCode = "200", description = "RETURNED")
  @ApiResponse(responseCode = "404", description = "resource.not_found (missing or not visible)")
  @ApiResponse(responseCode = "409", description = "state.conflict (not DEPLOYED)")
  public ResourceRequestResponse returnResource(
      @CurrentUser String username, @PathVariable long requestId) {
    return ResourceRequestResponse.from(requestService.returnResource(username, requestId));
  }

  private static RequestRole parseRole(String role) {
    try {
      return RequestRole.fromParameter(role);
    } catch (IllegalArgumentException ex) {
      throw FieldValidationException.of("role", "must be one of: requester, owner");
    }
  }
}
