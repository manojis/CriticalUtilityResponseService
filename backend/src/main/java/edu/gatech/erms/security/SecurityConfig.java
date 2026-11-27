package edu.gatech.erms.security;

import edu.gatech.erms.config.ErmsProperties;
import jakarta.servlet.DispatcherType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless JWT resource-server security (docs/ARCHITECTURE.md section 3.4).
 *
 * <ul>
 *   <li>Public: {@code POST /api/v1/auth/login}, the management endpoints (port 8081) and, only
 *       when docs are enabled, OpenAPI / Swagger UI.
 *   <li>Everything else requires {@code Authorization: Bearer <jwt>}; identity = {@code sub}.
 *   <li>401/403 are rendered as ProblemDetail by {@code GlobalExceptionHandler}.
 *   <li>CORS only when {@code erms.cors.allowed-origins} is non-empty (never {@code *}).
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

  /** springdoc serves Swagger UI below /api/swagger-ui so it is reachable via the /api ingress. */
  static final String[] API_DOCS_PATHS = {
    "/api/openapi", "/api/openapi/**", "/api/docs", "/api/docs/**", "/api/swagger-ui/**"
  };

  private static final int BCRYPT_STRENGTH = 10;

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      ErmsProperties properties,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
    AuthenticationEntryPoint entryPoint =
        (request, response, ex) -> exceptionResolver.resolveException(request, response, null, ex);
    AccessDeniedHandler accessDeniedHandler =
        (request, response, ex) -> exceptionResolver.resolveException(request, response, null, ex);

    http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> {
          auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
          auth.requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll();
          auth.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll();
          if (properties.apiDocsEnabled()) {
            auth.requestMatchers(API_DOCS_PATHS).permitAll();
          }
          auth.anyRequest().authenticated();
        })
        .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}).authenticationEntryPoint(entryPoint))
        .exceptionHandling(
            e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
        .addFilterAfter(new MdcUserFilter(), BearerTokenAuthenticationFilter.class);

    if (properties.cors().enabled()) {
      http.cors(cors -> cors.configurationSource(corsSource(properties.cors().allowedOrigins())));
    } else {
      http.cors(AbstractHttpConfigurer::disable);
    }
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
  }

  @Bean
  JwtEncoder jwtEncoder(ErmsProperties properties) {
    return NimbusJwtEncoder.withSecretKey(secretKey(properties)).build();
  }

  @Bean
  JwtDecoder jwtDecoder(ErmsProperties properties) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(secretKey(properties))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
    return decoder;
  }

  private static SecretKey secretKey(ErmsProperties properties) {
    byte[] bytes = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  private static UrlBasedCorsConfigurationSource corsSource(List<String> allowedOrigins) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(allowedOrigins);
    config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", RequestIdFilter.HEADER));
    config.setExposedHeaders(List.of("Location", RequestIdFilter.HEADER));
    config.setAllowCredentials(false);
    config.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }
}
