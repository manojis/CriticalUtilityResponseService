package edu.gatech.erms.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One resource search hit. {@code distanceKm} is {@code null} without an origin incident. */
public record ResourceSearchRow(
    long resourceId,
    String resourceName,
    String ownerUsername,
    String ownerName,
    BigDecimal cost,
    String costUnit,
    LocalDate nextAvailableDate,
    Double distanceKm) {

  public boolean available() {
    return nextAvailableDate == null;
  }
}
