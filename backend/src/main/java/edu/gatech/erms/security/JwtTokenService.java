package edu.gatech.erms.security;

import edu.gatech.erms.config.ErmsProperties;
import edu.gatech.erms.domain.UserAccount;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues HS256 access tokens with the claims documented in docs/API.md section 2. */
@Service
public class JwtTokenService {

  public static final String CLAIM_NAME = "name";
  public static final String CLAIM_USER_TYPE = "userType";

  private final JwtEncoder encoder;
  private final ErmsProperties.Jwt settings;
  private final Clock clock;

  public JwtTokenService(JwtEncoder encoder, ErmsProperties properties, Clock clock) {
    this.encoder = encoder;
    this.settings = properties.jwt();
    this.clock = clock;
  }

  public IssuedToken issue(UserAccount user) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(settings.issuer())
            .subject(user.username())
            .issuedAt(now)
            .expiresAt(now.plus(settings.ttl()))
            .claim(CLAIM_NAME, user.name())
            .claim(CLAIM_USER_TYPE, user.userType().name())
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new IssuedToken(token, settings.ttl().toSeconds());
  }
}
