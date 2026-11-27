package edu.gatech.erms.domain;

/** Credentials and identity of a user. The password hash never leaves the service layer. */
public record UserAccount(String username, String name, String passwordHash, UserType userType) {

  @Override
  public String toString() {
    return "UserAccount[username=" + username + ", userType=" + userType + "]";
  }
}
