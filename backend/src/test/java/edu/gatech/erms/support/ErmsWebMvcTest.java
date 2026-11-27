package edu.gatech.erms.support;

import edu.gatech.erms.config.ErmsProperties;
import edu.gatech.erms.security.SecurityConfig;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.ActiveProfiles;

/**
 * Web slice test with the real security configuration (JWT decoder, ProblemDetail 401/403) and the
 * {@code test} profile.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WebMvcTest
@Import(SecurityConfig.class)
@EnableConfigurationProperties(ErmsProperties.class)
@ActiveProfiles("test")
public @interface ErmsWebMvcTest {

  @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
  Class<?>[] value() default {};

  @AliasFor(annotation = WebMvcTest.class, attribute = "properties")
  String[] properties() default {};
}
