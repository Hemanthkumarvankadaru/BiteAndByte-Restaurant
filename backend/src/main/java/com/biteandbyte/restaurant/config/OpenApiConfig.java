package com.biteandbyte.restaurant.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI setup. Adds a bearer-token scheme so the "Authorize" button accepts the
 * access token from {@code /api/auth/login}. Public endpoints opt out per operation.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI biteAndByteOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("BiteAndByte Restaurant API")
                        .version("v1")
                        .description("Menu, cart, orders, payments and admin endpoints. "
                                + "Log in with /api/auth/login, then click Authorize and paste the accessToken."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
