package edu.gatech.erms.exception;

import org.springframework.http.HttpStatus;

/** The caller can see the entity but is not allowed to perform the action (HTTP 403). */
public class ForbiddenException extends ErmsException {

  private static final long serialVersionUID = 1L;

  public ForbiddenException(String message) {
    super(message);
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.FORBIDDEN;
  }

  @Override
  public String code() {
    return ErrorCodes.FORBIDDEN;
  }
}
