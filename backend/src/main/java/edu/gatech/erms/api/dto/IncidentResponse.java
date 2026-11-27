package edu.gatech.erms.api.dto;

import edu.gatech.erms.domain.Incident;
import java.time.LocalDate;

/** An incident. Coordinates are decimal degrees with up to 6 decimals. */
public record IncidentResponse(
    String incidentId,
    String declarationAbbreviation,
    LocalDate incidentDate,
    String description,
    double latitude,
    double longitude,
    String ownerUsername) {

  public static IncidentResponse from(Incident incident) {
    return new IncidentResponse(
        incident.incidentId(),
        incident.declarationAbbreviation(),
        incident.incidentDate(),
        incident.description(),
        incident.latitude().doubleValue(),
        incident.longitude().doubleValue(),
        incident.ownerUsername());
  }
}
