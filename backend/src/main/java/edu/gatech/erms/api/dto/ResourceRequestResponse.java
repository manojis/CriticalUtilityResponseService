package edu.gatech.erms.api.dto;

import edu.gatech.erms.domain.RequestStatus;
import edu.gatech.erms.domain.ResourceRequest;
import java.time.LocalDate;

/**
 * A resource request. {@code resourceAvailable} is true when the resource has no DEPLOYED request
 * (the UI uses it to enable "Deploy").
 */
public record ResourceRequestResponse(
    long requestId,
    long resourceId,
    String resourceName,
    String ownerUsername,
    String incidentId,
    String incidentDescription,
    String requestedBy,
    RequestStatus status,
    LocalDate requestDate,
    LocalDate expectedReturnDate,
    LocalDate deployDate,
    LocalDate actualReturnDate,
    boolean resourceAvailable) {

  public static ResourceRequestResponse from(ResourceRequest r) {
    return new ResourceRequestResponse(
        r.requestId(),
        r.resourceId(),
        r.resourceName(),
        r.ownerUsername(),
        r.incidentId(),
        r.incidentDescription(),
        r.requestedBy(),
        r.status(),
        r.requestDate(),
        r.expectedReturnDate(),
        r.deployDate(),
        r.actualReturnDate(),
        r.resourceAvailable());
  }
}
