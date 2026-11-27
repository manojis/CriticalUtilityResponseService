package edu.gatech.erms.api.dto;

import edu.gatech.erms.domain.UserType;

/** Successful login: a Bearer token and the authenticated user. */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, User user) {

  public static final String BEARER = "Bearer";

  /** Minimal identity returned with the token. */
  public record User(String username, String name, UserType userType) {}

  @Override
  public String toString() {
    return "LoginResponse[accessToken=****, tokenType="
        + tokenType
        + ", expiresIn="
        + expiresIn
        + ", user="
        + user
        + "]";
  }
}
