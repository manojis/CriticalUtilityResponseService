package edu.gatech.erms.service;

import edu.gatech.erms.domain.UserAccount;
import edu.gatech.erms.exception.InvalidCredentialsException;
import edu.gatech.erms.repository.UserRepository;
import edu.gatech.erms.security.IssuedToken;
import edu.gatech.erms.security.JwtTokenService;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Verifies credentials against BCrypt hashes and issues JWTs (legacy defects S2, S7). Unknown users
 * and wrong passwords fail identically, and the unknown-user path still performs a BCrypt
 * comparison so response timing does not reveal which usernames exist.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService tokens;
  private final String dummyHash;

  public AuthService(
      UserRepository users, PasswordEncoder passwordEncoder, JwtTokenService tokens) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.tokens = tokens;
    this.dummyHash = passwordEncoder.encode("erms-timing-guard-" + UUID.randomUUID());
  }

  /**
   * Authenticates a user.
   *
   * @throws InvalidCredentialsException for an unknown user or a wrong password
   */
  public LoginResult login(String username, String password) {
    Optional<UserAccount> account = users.findAccount(username);
    if (account.isEmpty()) {
      passwordEncoder.matches(password, dummyHash);
      log.info("Login failed");
      throw new InvalidCredentialsException();
    }
    UserAccount user = account.get();
    if (!passwordEncoder.matches(password, user.passwordHash())) {
      log.info("Login failed");
      throw new InvalidCredentialsException();
    }
    IssuedToken token = tokens.issue(user);
    log.info("Login succeeded for user {}", user.username());
    return new LoginResult(token, user);
  }

  /** A successful login: the token plus the authenticated account. */
  public record LoginResult(IssuedToken token, UserAccount user) {}
}
