package edu.gatech.erms.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Input for {@link ResourceService#create}. {@code additionalEsfs} and {@code capabilities} may be
 * {@code null} (treated as empty, legacy defect R7).
 */
public record CreateResourceCommand(
    String resourceName,
    String model,
    Integer primaryEsf,
    List<Integer> additionalEsfs,
    List<String> capabilities,
    BigDecimal cost,
    String costUnit,
    BigDecimal latitude,
    BigDecimal longitude,
    BigDecimal maxDistanceKm) {}
