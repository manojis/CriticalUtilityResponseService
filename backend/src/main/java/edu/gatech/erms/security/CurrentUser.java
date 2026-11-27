package edu.gatech.erms.security;

import io.swagger.v3.oas.annotations.Parameter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * Injects the caller's username (the JWT {@code sub} claim) into a controller parameter. This is
 * the only source of identity: no endpoint accepts a username from the client (legacy defect S3).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "subject", errorOnInvalidType = true)
@Parameter(hidden = true)
public @interface CurrentUser {}
