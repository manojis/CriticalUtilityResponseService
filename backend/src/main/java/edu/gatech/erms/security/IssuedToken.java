package edu.gatech.erms.security;

/** A signed access token and its lifetime in seconds. */
public record IssuedToken(String accessToken, long expiresInSeconds) {

  @Override
  public String toString() {
    return "IssuedToken[accessToken=****, expiresInSeconds=" + expiresInSeconds + "]";
  }
}
