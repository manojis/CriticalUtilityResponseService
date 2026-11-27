package edu.gatech.erms.domain;

import java.math.BigDecimal;

/**
 * Normalised search filters. All filters are optional and AND-combined. {@code origin} is the
 * caller's incident used as the distance reference point.
 */
public record ResourceSearchCriteria(
    String keyword, Integer esf, Incident origin, BigDecimal distanceKm, int page, int size) {

  public long offset() {
    return (long) page * size;
  }
}
