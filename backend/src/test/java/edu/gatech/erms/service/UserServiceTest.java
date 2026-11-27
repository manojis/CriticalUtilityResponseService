package edu.gatech.erms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.gatech.erms.domain.UserProfile;
import edu.gatech.erms.domain.UserType;
import edu.gatech.erms.exception.NotFoundException;
import edu.gatech.erms.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserServiceTest {

  private final UserRepository users = mock(UserRepository.class);
  private final UserService service = new UserService(users);

  @Test
  void returnsProfile() {
    UserProfile profile =
        new UserProfile("acme", "Acme", UserType.COMPANY, null, null, null, null, "Atlanta", 250L);
    when(users.findProfile("acme")).thenReturn(Optional.of(profile));
    assertThat(service.getProfile("acme")).isEqualTo(profile);
  }

  @Test
  void missingUserIsNotFound_F19() {
    when(users.findProfile("ghost")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.getProfile("ghost"))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("User ghost not found");
  }
}
