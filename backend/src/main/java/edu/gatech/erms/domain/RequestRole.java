package edu.gatech.erms.domain;

import java.util.Locale;

/** Perspective used when listing resource requests. */
public enum RequestRole {
  /** Requests the caller made (for the caller's incidents). */
  REQUESTER,
  /** Requests for resources the caller owns. */
  OWNER;

  /**
   * Parses the lower-case query parameter form ({@code requester}/{@code owner}).
   *
   * @throws IllegalArgumentException if the value is not a known role
   */
  public static RequestRole fromParameter(String value) {
    return RequestRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
  }
}
