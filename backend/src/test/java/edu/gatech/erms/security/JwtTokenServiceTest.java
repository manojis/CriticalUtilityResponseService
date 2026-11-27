package edu.gatech.erms.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.gatech.erms.config.ErmsProperties;
import edu.gatech.erms.domain.UserAccount;
import edu.gatech.erms.domain.UserType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtTokenServiceTest {

  private static final String SECRET = "unit-test-secret-0123456789-abcdefghijkl";
  private static final UserAccount ALICE =
      new UserAccount("alice", "Alice Smith", "hash", UserType.INDIVIDUAL);

  private final SecurityConfig config = new SecurityConfig();

  private static ErmsProperties properties(String issuer, Duration ttl) {
    return new ErmsProperties(
        new ErmsProperties.Jwt(SECRET, ttl, issuer),
        new ErmsProperties.Cors(List.of()),
        "UTC",
        false);
  }

  @Test
  void issuesTokenWithDocumentedClaims() {
    ErmsProperties props = properties("erms", Duration.ofHours(1));
    JwtTokenService service =
        new JwtTokenService(config.jwtEncoder(props), props, Clock.systemUTC());

    IssuedToken token = service.issue(ALICE);
    Jwt jwt = config.jwtDecoder(props).decode(token.accessToken());

    assertThat(token.expiresInSeconds()).isEqualTo(3600);
    assertThat(jwt.getSubject()).isEqualTo("alice");
    assertThat(jwt.getClaimAsString("iss")).isEqualTo("erms");
    assertThat(jwt.getClaimAsString("name")).isEqualTo("Alice Smith");
    assertThat(jwt.getClaimAsString("userType")).isEqualTo("INDIVIDUAL");
    assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
        .isEqualTo(Duration.ofHours(1));
    assertThat(token.toString()).doesNotContain(token.accessToken());
  }

  @Test
  void decoderRejectsExpiredToken() {
    ErmsProperties props = properties("erms", Duration.ofMinutes(5));
    Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
    String token =
        new JwtTokenService(config.jwtEncoder(props), props, past).issue(ALICE).accessToken();

    JwtDecoder decoder = config.jwtDecoder(props);
    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void decoderRejectsForeignIssuer() {
    ErmsProperties foreign = properties("someone-else", Duration.ofHours(1));
    String token =
        new JwtTokenService(config.jwtEncoder(foreign), foreign, Clock.systemUTC())
            .issue(ALICE)
            .accessToken();

    JwtDecoder decoder = config.jwtDecoder(properties("erms", Duration.ofHours(1)));
    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void decoderRejectsTokenSignedWithAnotherSecret() {
    ErmsProperties other =
        new ErmsProperties(
            new ErmsProperties.Jwt(
                "another-secret-0123456789-abcdefghijklmn", Duration.ofHours(1), "erms"),
            new ErmsProperties.Cors(List.of()),
            "UTC",
            false);
    String token =
        new JwtTokenService(config.jwtEncoder(other), other, Clock.systemUTC())
            .issue(ALICE)
            .accessToken();

    JwtDecoder decoder = config.jwtDecoder(properties("erms", Duration.ofHours(1)));
    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }
}
