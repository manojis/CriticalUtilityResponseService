package edu.gatech.erms.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/auth/login} body. */
public record LoginRequest(
    @NotBlank @Size(max = 50) String username, @NotBlank @Size(max = 100) String password) {

  @Override
  public String toString() {
    return "LoginRequest[username=" + username + ", password=****]";
  }
}
