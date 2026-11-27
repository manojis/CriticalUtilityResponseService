package edu.gatech.erms.api.dto;

import edu.gatech.erms.domain.ResourceDetail;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Full resource view. {@code nextAvailableDate} is present only while the resource is in use. */
public record ResourceDetailResponse(
    long resourceId,
    String resourceName,
    String model,
    String ownerUsername,
    String ownerName,
    EsfResponse primaryEsf,
    List<EsfResponse> additionalEsfs,
    List<String> capabilities,
    BigDecimal cost,
    String costUnit,
    double latitude,
    double longitude,
    Double maxDistanceKm,
    boolean available,
    LocalDate nextAvailableDate) {

  public static ResourceDetailResponse from(ResourceDetail r) {
    return new ResourceDetailResponse(
        r.resourceId(),
        r.resourceName(),
        r.model(),
        r.ownerUsername(),
        r.ownerName(),
        EsfResponse.from(r.primaryEsf()),
        r.additionalEsfs().stream().map(EsfResponse::from).toList(),
        r.capabilities(),
        r.cost(),
        r.costUnit(),
        r.latitude().doubleValue(),
        r.longitude().doubleValue(),
        r.maxDistanceKm() == null ? null : r.maxDistanceKm().doubleValue(),
        r.available(),
        r.nextAvailableDate());
  }
}
