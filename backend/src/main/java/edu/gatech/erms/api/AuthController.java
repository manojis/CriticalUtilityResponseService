package edu.gatech.erms.api;

import edu.gatech.erms.api.dto.LoginRequest;
import edu.gatech.erms.api.dto.LoginResponse;
import edu.gatech.erms.service.AuthService;
import edu.gatech.erms.service.AuthService.LoginResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login: exchanges username/password for a JWT. */
@RestController
@RequestMapping(ApiPaths.V1 + "/auth")
@Tag(name = "Auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  @SecurityRequirements
  @Operation(summary = "Log in and obtain a Bearer access token")
  @ApiResponse(responseCode = "200", description = "Authenticated")
  @ApiResponse(responseCode = "400", description = "validation.failed / request.malformed")
  @ApiResponse(responseCode = "401", description = "auth.invalid_credentials")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    LoginResult result = authService.login(request.username(), request.password());
    return new LoginResponse(
        result.token().accessToken(),
        LoginResponse.BEARER,
        result.token().expiresInSeconds(),
        new LoginResponse.User(
            result.user().username(), result.user().name(), result.user().userType()));
  }
}
