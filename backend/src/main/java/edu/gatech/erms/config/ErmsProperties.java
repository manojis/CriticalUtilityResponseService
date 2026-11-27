package edu.gatech.erms.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings bound from the {@code erms.*} namespace (see docs/ARCHITECTURE.md section
 * 3.3 for the matching environment variables).
 *
 * @param jwt token signing settings
 * @param cors CORS allow-list (empty = CORS disabled)
 * @param timeZone zone used to compute "today"
 * @param apiDocsEnabled whether OpenAPI / Swagger UI are served (and therefore public)
 */
@ConfigurationProperties("erms")
public record ErmsProperties(
    @DefaultValue Jwt jwt,
    @DefaultValue Cors cors,
    @DefaultValue("UTC") String timeZone,
    @DefaultValue("false") boolean apiDocsEnabled) {

  /** Minimum HS256 key length in bytes (256 bits). */
  public static final int MIN_SECRET_BYTES = 32;

  /**
   * JWT settings.
   *
   * @param secret HS256 signing secret, at least 32 bytes (required)
   * @param ttl token lifetime
   * @param issuer {@code iss} claim issued and required
   */
  public record Jwt(
      String secret, @DefaultValue("PT1H") Duration ttl, @DefaultValue("erms") String issuer) {

    public Jwt {
      if (secret == null || secret.isBlank()) {
        throw new IllegalStateException(
            "erms.jwt.secret (env ERMS_JWT_SECRET) is required and must be at least "
                + MIN_SECRET_BYTES
                + " bytes");
      }
      if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
        throw new IllegalStateException(
            "erms.jwt.secret (env ERMS_JWT_SECRET) must be at least "
                + MIN_SECRET_BYTES
                + " bytes");
      }
      if (ttl == null || ttl.isNegative() || ttl.isZero()) {
        throw new IllegalStateException("erms.jwt.ttl must be a positive duration");
      }
    }

    @Override
    public String toString() {
      return "Jwt[secret=****, ttl=" + ttl + ", issuer=" + issuer + "]";
    }
  }

  /**
   * CORS settings.
   *
   * @param allowedOrigins exact origins allowed to call the API from a browser
   */
  public record Cors(@DefaultValue List<String> allowedOrigins) {

    public Cors {
      allowedOrigins =
          allowedOrigins == null
              ? List.of()
              : allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public boolean enabled() {
      return !allowedOrigins.isEmpty();
    }
  }
}
