package edu.gatech.erms.exception;

import org.springframework.http.HttpStatus;

/** Unknown user or wrong password. Deliberately does not say which (HTTP 401). */
public class InvalidCredentialsException extends ErmsException {

  private static final long serialVersionUID = 1L;

  public InvalidCredentialsException() {
    super("Invalid username or password");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.UNAUTHORIZED;
  }

  @Override
  public String code() {
    return ErrorCodes.INVALID_CREDENTIALS;
  }
}
