package edu.gatech.erms.api;

import edu.gatech.erms.api.dto.UserProfileResponse;
import edu.gatech.erms.security.CurrentUser;
import edu.gatech.erms.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own profile. */
@RestController
@RequestMapping(ApiPaths.V1 + "/me")
@Tag(name = "Profile")
public class MeController {

  private final UserService userService;

  public MeController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  @Operation(summary = "Profile of the authenticated user")
  @ApiResponse(responseCode = "200", description = "Profile (type-specific fields only)")
  @ApiResponse(responseCode = "401", description = "auth.unauthorized")
  @ApiResponse(responseCode = "404", description = "resource.not_found (user no longer exists)")
  public UserProfileResponse me(@CurrentUser String username) {
    return UserProfileResponse.from(userService.getProfile(username));
  }
}
