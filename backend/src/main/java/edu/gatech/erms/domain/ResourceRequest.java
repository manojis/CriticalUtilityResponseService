package edu.gatech.erms.domain;

import java.time.LocalDate;

/** A request for a resource on behalf of an incident, joined with display data. */
public record ResourceRequest(
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

  public boolean isRequester(String username) {
    return requestedBy.equals(username);
  }

  public boolean isOwner(String username) {
    return ownerUsername.equals(username);
  }
}
