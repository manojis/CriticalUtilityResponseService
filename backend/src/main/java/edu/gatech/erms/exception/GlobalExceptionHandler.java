package edu.gatech.erms.exception;

import edu.gatech.erms.security.RequestIdFilter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every exception to an RFC 9457 ProblemDetail with the extension properties {@code code},
 * {@code requestId} and (for validation errors) {@code errors}. Unexpected exceptions produce a
 * generic 500 and never echo the exception message (legacy defect S6).
 *
 * <p>Spring Security's entry point and access-denied handler delegate here too, so 401/403
 * responses share the same shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  static final String VALIDATION_DETAIL = "Request validation failed";

  /** RFC 9457 default problem type; serialised explicitly so clients always see it. */
  private static final URI ABOUT_BLANK = URI.create("about:blank");

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ErmsException.class)
  ResponseEntity<Object> handleErms(ErmsException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
    enrich(problem, ex.code());
    if (ex instanceof FieldValidationException validation) {
      problem.setProperty("errors", sorted(validation.errors()));
    }
    log.debug("Request rejected: {} {}", ex.status().value(), ex.getMessage());
    return problemResponse(problem);
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<Object> handleAuthentication(AuthenticationException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.UNAUTHORIZED, "Missing, invalid or expired access token");
    enrich(problem, ErrorCodes.UNAUTHORIZED);
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
    enrich(problem, ErrorCodes.FORBIDDEN);
    return problemResponse(problem);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex) {
    String detail =
        ex instanceof DuplicateKeyException
            ? "The request conflicts with existing data"
            : "The request violates a data constraint";
    log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
    enrich(problem, ErrorCodes.CONFLICT);
    return problemResponse(problem);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    enrich(problem, ErrorCodes.INTERNAL);
    return problemResponse(problem);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldValidationException.FieldError> errors = new ArrayList<>();
    for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
      errors.add(
          new FieldValidationException.FieldError(
              fieldError.getField(), messageOf(fieldError.getDefaultMessage())));
    }
    ex.getBindingResult()
        .getGlobalErrors()
        .forEach(error ->
            errors.add(
                new FieldValidationException.FieldError(
                    error.getObjectName(), messageOf(error.getDefaultMessage()))));
    return validationResponse(errors);
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldValidationException.FieldError> errors = new ArrayList<>();
    ex.getParameterValidationResults()
        .forEach(result -> {
          String name = result.getMethodParameter().getParameterName();
          for (MessageSourceResolvable error : result.getResolvableErrors()) {
            errors.add(
                new FieldValidationException.FieldError(
                    name == null ? "parameter" : name, messageOf(error.getDefaultMessage())));
          }
        });
    return validationResponse(errors);
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ResponseEntity<Object> response =
        super.handleExceptionInternal(ex, body, headers, statusCode, request);
    if (response != null && response.getBody() instanceof ProblemDetail problem) {
      if (ex instanceof NoResourceFoundException) {
        problem.setDetail("No endpoint matches the request path");
      }
      if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
        enrich(problem, ErrorCodes.forStatus(statusCode));
      }
    }
    if (statusCode.is5xxServerError()) {
      log.error("Request failed with {}", statusCode.value(), ex);
    } else {
      log.debug("Request rejected with {}: {}", statusCode.value(), ex.getMessage());
    }
    return response;
  }

  private ResponseEntity<Object> validationResponse(
      List<FieldValidationException.FieldError> errors) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_DETAIL);
    enrich(problem, ErrorCodes.VALIDATION_FAILED);
    problem.setProperty("errors", sorted(errors));
    return problemResponse(problem);
  }

  private static List<FieldValidationException.FieldError> sorted(
      List<FieldValidationException.FieldError> errors) {
    return errors.stream()
        .sorted(
            Comparator.comparing(FieldValidationException.FieldError::field)
                .thenComparing(FieldValidationException.FieldError::message))
        .toList();
  }

  private static String messageOf(@Nullable String message) {
    return message == null ? "is invalid" : message;
  }

  private static ResponseEntity<Object> problemResponse(ProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  private static void enrich(ProblemDetail problem, String code) {
    if (problem.getType() == null) {
      problem.setType(ABOUT_BLANK);
    }
    problem.setProperty("code", code);
    String requestId = MDC.get(RequestIdFilter.MDC_KEY);
    if (requestId != null) {
      problem.setProperty("requestId", requestId);
    }
  }
}
