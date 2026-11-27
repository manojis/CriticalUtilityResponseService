package edu.gatech.erms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata, served at {@code /api/openapi} when {@code ERMS_API_DOCS_ENABLED=true}. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

  static final String BEARER = "bearerAuth";

  @Bean
  OpenAPI ermsOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("ERMS REST API")
                .version("v1")
                .description(
                    "Emergency Resource Management System. Contract: docs/API.md. All endpoints"
                        + " except POST /api/v1/auth/login require a Bearer JWT."))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER));
  }
}
