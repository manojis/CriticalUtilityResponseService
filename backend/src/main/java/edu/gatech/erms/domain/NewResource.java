package edu.gatech.erms.domain;

import java.math.BigDecimal;
import java.util.List;

/** A validated, normalised resource ready to be persisted. */
public record NewResource(
    String ownerUsername,
    String resourceName,
    String model,
    int primaryEsf,
    List<Integer> additionalEsfs,
    List<String> capabilities,
    BigDecimal cost,
    String costUnit,
    BigDecimal latitude,
    BigDecimal longitude,
    BigDecimal maxDistanceKm) {

  public NewResource {
    additionalEsfs = List.copyOf(additionalEsfs);
    capabilities = List.copyOf(capabilities);
  }
}
