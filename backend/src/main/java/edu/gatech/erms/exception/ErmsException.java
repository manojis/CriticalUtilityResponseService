package edu.gatech.erms.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for expected business errors. The message is safe to show to API clients and is used
 * as the ProblemDetail {@code detail}.
 */
public abstract class ErmsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  protected ErmsException(String message) {
    super(message);
  }

  /** HTTP status the error maps to. */
  public abstract HttpStatus status();

  /** Machine-readable ProblemDetail {@code code}. */
  public abstract String code();
}
