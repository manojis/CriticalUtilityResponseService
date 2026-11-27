package edu.gatech.erms.exception;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * A business validation rule that Bean Validation cannot express (e.g. "declaration must exist",
 * "date not after today") failed for one or more fields (HTTP 400 {@code validation.failed}).
 */
public class FieldValidationException extends ErmsException {

  private static final long serialVersionUID = 1L;

  private final transient List<FieldError> errors;

  public FieldValidationException(List<FieldError> errors) {
    super("Request validation failed");
    this.errors = List.copyOf(errors);
  }

  public static FieldValidationException of(String field, String message) {
    return new FieldValidationException(List.of(new FieldError(field, message)));
  }

  public List<FieldError> errors() {
    return errors;
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.BAD_REQUEST;
  }

  @Override
  public String code() {
    return ErrorCodes.VALIDATION_FAILED;
  }

  /** One invalid field and the reason. */
  public record FieldError(String field, String message) {}
}
