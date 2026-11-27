package edu.gatech.erms.service;

import java.time.LocalDate;

/** Input for {@link ResourceRequestService#create}. */
public record CreateResourceRequestCommand(
    long resourceId, String incidentId, LocalDate expectedReturnDate, boolean deployImmediately) {}
