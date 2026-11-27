package edu.gatech.erms.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ErmsPropertiesTest {

  @Test
  void missingSecretFailsFast() {
    assertThatThrownBy(() -> new ErmsProperties.Jwt(null, Duration.ofHours(1), "erms"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ERMS_JWT_SECRET");
    assertThatThrownBy(() -> new ErmsProperties.Jwt("  ", Duration.ofHours(1), "erms"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shortSecretFailsFast() {
    assertThatThrownBy(() -> new ErmsProperties.Jwt("too-short", Duration.ofHours(1), "erms"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32 bytes");
  }

  @Test
  void nonPositiveTtlFailsFast() {
    String secret = "x".repeat(32);
    assertThatThrownBy(() -> new ErmsProperties.Jwt(secret, Duration.ZERO, "erms"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void secretIsMaskedInToString() {
    ErmsProperties.Jwt jwt = new ErmsProperties.Jwt("s".repeat(40), Duration.ofHours(1), "erms");
    assertThat(jwt.toString()).doesNotContain("ssss").contains("****");
  }

  @Test
  void corsOriginsAreTrimmedAndBlankMeansDisabled() {
    ErmsProperties.Cors cors =
        new ErmsProperties.Cors(Arrays.asList(" http://localhost:5173 ", "", "  "));
    assertThat(cors.allowedOrigins()).containsExactly("http://localhost:5173");
    assertThat(cors.enabled()).isTrue();
    assertThat(new ErmsProperties.Cors(null).enabled()).isFalse();
  }
}
