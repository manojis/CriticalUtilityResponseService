package edu.gatech.erms.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Input for {@link IncidentService#create}. Bean-validated at the API boundary. */
public record CreateIncidentCommand(
    String declarationAbbreviation,
    LocalDate incidentDate,
    String description,
    BigDecimal latitude,
    BigDecimal longitude) {}
