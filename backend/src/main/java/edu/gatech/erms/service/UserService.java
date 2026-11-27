package edu.gatech.erms.service;

import edu.gatech.erms.domain.UserProfile;
import edu.gatech.erms.exception.NotFoundException;
import edu.gatech.erms.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User profiles. */
@Service
public class UserService {

  private final UserRepository users;

  public UserService(UserRepository users) {
    this.users = users;
  }

  /**
   * Returns the profile of a user.
   *
   * @throws NotFoundException if the user does not exist (legacy defect F19: 404, not 204)
   */
  @Transactional(readOnly = true)
  public UserProfile getProfile(String username) {
    return users.findProfile(username).orElseThrow(() -> NotFoundException.of("User", username));
  }
}
