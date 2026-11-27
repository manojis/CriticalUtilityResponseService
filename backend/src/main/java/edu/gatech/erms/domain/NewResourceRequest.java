package edu.gatech.erms.domain;

import java.time.LocalDate;

/** A resource request ready to be persisted. */
public record NewResourceRequest(
    long resourceId,
    String incidentId,
    String requestedBy,
    RequestStatus status,
    LocalDate requestDate,
    LocalDate expectedReturnDate,
    LocalDate deployDate) {}
