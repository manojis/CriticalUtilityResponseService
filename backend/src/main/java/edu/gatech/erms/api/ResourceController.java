package edu.gatech.erms.api;

import edu.gatech.erms.api.dto.CreateResourceRequest;
import edu.gatech.erms.api.dto.PageResponse;
import edu.gatech.erms.api.dto.ResourceDetailResponse;
import edu.gatech.erms.api.dto.ResourceSearchResultResponse;
import edu.gatech.erms.domain.ResourceDetail;
import edu.gatech.erms.security.CurrentUser;
import edu.gatech.erms.service.ResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Resource registration, lookup and search. */
@RestController
@RequestMapping(ApiPaths.V1 + "/resources")
@Tag(name = "Resources")
@ApiResponse(responseCode = "401", description = "auth.unauthorized")
public class ResourceController {

  private final ResourceService resourceService;

  public ResourceController(ResourceService resourceService) {
    this.resourceService = resourceService;
  }

  @PostMapping
  @Operation(summary = "Register a resource owned by the caller")
  @ApiResponse(responseCode = "201", description = "Created")
  @ApiResponse(responseCode = "400", description = "validation.failed / request.malformed")
  public ResponseEntity<ResourceDetailResponse> create(
      @CurrentUser String username, @Valid @RequestBody CreateResourceRequest request) {
    ResourceDetail resource = resourceService.create(username, request.toCommand());
    return ResponseEntity.created(URI.create(ApiPaths.V1 + "/resources/" + resource.resourceId()))
        .body(ResourceDetailResponse.from(resource));
  }

  @GetMapping("/{resourceId}")
  @Operation(summary = "Any resource by id")
  @ApiResponse(responseCode = "200", description = "OK")
  @ApiResponse(responseCode = "400", description = "request.malformed (non-numeric id)")
  @ApiResponse(responseCode = "404", description = "resource.not_found")
  public ResourceDetailResponse get(@PathVariable long resourceId) {
    return ResourceDetailResponse.from(resourceService.get(resourceId));
  }

  @GetMapping
  @Operation(
      summary = "Search resources",
      description =
          "All filters optional and AND-combined. With incidentId: distanceKm per row, nearest"
              + " first, resources outside their own max distance excluded.")
  @ApiResponse(responseCode = "200", description = "OK")
  @ApiResponse(
      responseCode = "400",
      description = "validation.failed (e.g. distanceKm without incidentId, size > 100)")
  @ApiResponse(responseCode = "404", description = "resource.not_found (incident not the caller's)")
  public PageResponse<ResourceSearchResultResponse> search(
      @CurrentUser String username,
      @Parameter(description = "Substring of name, model or any capability (case-insensitive)")
          @RequestParam(required = false)
          @Size(max = 100)
          String keyword,
      @Parameter(description = "Primary or additional ESF number")
          @RequestParam(required = false)
          @Positive
          Integer esf,
      @Parameter(description = "One of the caller's incidents; enables distance")
          @RequestParam(required = false)
          @Size(max = 16)
          String incidentId,
      @Parameter(description = "Max distance in km from the incident (requires incidentId)")
          @RequestParam(required = false)
          @Positive
          BigDecimal distanceKm,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(
        resourceService.search(username, keyword, esf, incidentId, distanceKm, page, size),
        ResourceSearchResultResponse::from);
  }
}
