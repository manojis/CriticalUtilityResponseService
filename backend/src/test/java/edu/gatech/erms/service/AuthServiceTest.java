package edu.gatech.erms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.gatech.erms.domain.UserAccount;
import edu.gatech.erms.domain.UserType;
import edu.gatech.erms.exception.InvalidCredentialsException;
import edu.gatech.erms.repository.UserRepository;
import edu.gatech.erms.security.IssuedToken;
import edu.gatech.erms.security.JwtTokenService;
import edu.gatech.erms.support.TestData;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final UserAccount ALICE =
      new UserAccount("alice", "Alice Smith", TestData.PASSWORD_HASH, UserType.INDIVIDUAL);

  @Mock private UserRepository users;
  @Mock private JwtTokenService tokens;
  @Spy private PasswordEncoder encoder = new BCryptPasswordEncoder(4);

  private AuthService service;

  @BeforeEach
  void setUp() {
    service = new AuthService(users, encoder, tokens);
  }

  @Test
  void validCredentialsIssueToken_S2() {
    when(users.findAccount("alice")).thenReturn(Optional.of(ALICE));
    when(tokens.issue(ALICE)).thenReturn(new IssuedToken("jwt", 3600));

    AuthService.LoginResult result = service.login("alice", "password");

    assertThat(result.token().accessToken()).isEqualTo("jwt");
    assertThat(result.user()).isEqualTo(ALICE);
  }

  @Test
  void wrongPasswordIsRejectedWithGenericError_S7() {
    when(users.findAccount("alice")).thenReturn(Optional.of(ALICE));

    assertThatThrownBy(() -> service.login("alice", "wrong"))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid username or password");
    verify(tokens, never()).issue(any());
  }

  @Test
  void unknownUserGetsSameErrorAndStillRunsBcrypt_S2() {
    when(users.findAccount("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.login("ghost", "password"))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid username or password");
    // constant-time guard: a BCrypt comparison happens even though the user does not exist
    verify(encoder).matches(eq("password"), anyString());
    verify(tokens, never()).issue(any());
  }
}
