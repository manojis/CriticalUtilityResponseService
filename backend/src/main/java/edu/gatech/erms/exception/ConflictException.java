package edu.gatech.erms.exception;

import org.springframework.http.HttpStatus;

/** Illegal state transition or uniqueness violation (HTTP 409). */
public class ConflictException extends ErmsException {

  private static final long serialVersionUID = 1L;

  public ConflictException(String message) {
    super(message);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.CONFLICT;
  }

  @Override
  public String code() {
    return ErrorCodes.CONFLICT;
  }
}
