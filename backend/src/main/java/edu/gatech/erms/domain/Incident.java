package edu.gatech.erms.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A declared incident. {@code incidentId} has the form {@code <ABBR>-<n>}. */
public record Incident(
    String incidentId,
    String declarationAbbreviation,
    LocalDate incidentDate,
    String description,
    BigDecimal latitude,
    BigDecimal longitude,
    String ownerUsername) {}
