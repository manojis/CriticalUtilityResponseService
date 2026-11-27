package edu.gatech.erms.exception;

import org.springframework.http.HttpStatusCode;

/** ProblemDetail {@code code} values (docs/API.md section 3). */
public final class ErrorCodes {

  public static final String VALIDATION_FAILED = "validation.failed";
  public static final String MALFORMED = "request.malformed";
  public static final String UNAUTHORIZED = "auth.unauthorized";
  public static final String INVALID_CREDENTIALS = "auth.invalid_credentials";
  public static final String FORBIDDEN = "auth.forbidden";
  public static final String NOT_FOUND = "resource.not_found";
  public static final String METHOD_NOT_ALLOWED = "request.method_not_allowed";
  public static final String UNSUPPORTED_MEDIA_TYPE = "request.unsupported_media_type";
  public static final String CONFLICT = "state.conflict";
  public static final String INTERNAL = "internal.error";

  private ErrorCodes() {}

  /** Default code for a status when no more specific code applies. */
  public static String forStatus(HttpStatusCode status) {
    return switch (status.value()) {
      case 401 -> UNAUTHORIZED;
      case 403 -> FORBIDDEN;
      case 404 -> NOT_FOUND;
      case 405 -> METHOD_NOT_ALLOWED;
      case 409 -> CONFLICT;
      case 415 -> UNSUPPORTED_MEDIA_TYPE;
      default -> status.is5xxServerError() ? INTERNAL : MALFORMED;
    };
  }
}
