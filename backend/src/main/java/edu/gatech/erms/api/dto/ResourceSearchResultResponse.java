package edu.gatech.erms.api.dto;

import edu.gatech.erms.domain.ResourceSearchRow;
import java.math.BigDecimal;
import java.time.LocalDate;

/** One search hit. {@code distanceKm} (0.1 km precision) is present only with an incident. */
public record ResourceSearchResultResponse(
    long resourceId,
    String resourceName,
    String ownerUsername,
    String ownerName,
    BigDecimal cost,
    String costUnit,
    boolean available,
    LocalDate nextAvailableDate,
    Double distanceKm) {

  public static ResourceSearchResultResponse from(ResourceSearchRow row) {
    return new ResourceSearchResultResponse(
        row.resourceId(),
        row.resourceName(),
        row.ownerUsername(),
        row.ownerName(),
        row.cost(),
        row.costUnit(),
        row.available(),
        row.nextAvailableDate(),
        row.distanceKm());
  }
}
