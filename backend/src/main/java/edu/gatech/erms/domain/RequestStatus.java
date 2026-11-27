package edu.gatech.erms.domain;

/** Lifecycle states of a resource request (see docs/FLOWS.md, "Resource request lifecycle"). */
public enum RequestStatus {
  PENDING,
  DEPLOYED,
  REJECTED,
  CANCELLED,
  RETURNED
}
