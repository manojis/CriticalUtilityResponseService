package edu.gatech.erms.exception;

import org.springframework.http.HttpStatus;

/** The entity does not exist or is not visible to the caller (HTTP 404). */
public class NotFoundException extends ErmsException {

  private static final long serialVersionUID = 1L;

  public NotFoundException(String message) {
    super(message);
  }

  public static NotFoundException of(String entity, Object id) {
    return new NotFoundException(entity + " " + id + " not found");
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.NOT_FOUND;
  }

  @Override
  public String code() {
    return ErrorCodes.NOT_FOUND;
  }
}
