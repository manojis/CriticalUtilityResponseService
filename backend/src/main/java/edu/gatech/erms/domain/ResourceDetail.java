package edu.gatech.erms.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Full view of a resource. Availability is derived: {@code nextAvailableDate} is the expected
 * return date of the current DEPLOYED request, or {@code null} when the resource is available.
 */
public record ResourceDetail(
    long resourceId,
    String resourceName,
    String model,
    String ownerUsername,
    String ownerName,
    Esf primaryEsf,
    List<Esf> additionalEsfs,
    List<String> capabilities,
    BigDecimal cost,
    String costUnit,
    BigDecimal latitude,
    BigDecimal longitude,
    BigDecimal maxDistanceKm,
    LocalDate nextAvailableDate) {

  public ResourceDetail {
    additionalEsfs = List.copyOf(additionalEsfs);
    capabilities = List.copyOf(capabilities);
  }

  public boolean available() {
    return nextAvailableDate == null;
  }
}
