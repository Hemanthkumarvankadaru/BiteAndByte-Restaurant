package com.biteandbyte.restaurant.config;

import com.biteandbyte.restaurant.security.JsonAuthErrorHandlers;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Who can call what. Stateless: every request carries a JWT access token in the
 * Authorization header, checked by Spring's OAuth2 resource server support.
 *
 * <p>Rules are matched top to bottom, so specific paths come before broad ones.
 */
@Configuration
public class SecurityConfig {

    /** Auth endpoints that must work even when the client still sends an old, expired token. */
    private static final Set<String> TOKENLESS_AUTH_PATHS = Set.of(
            "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonAuthErrorHandlers authErrors,
                                            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // H2 console renders in frames from the same origin.
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))
            .authorizeHttpRequests(auth -> auth
                // Infrastructure
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                // Public
                .requestMatchers(HttpMethod.POST, TOKENLESS_AUTH_PATHS.toArray(String[]::new)).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/menu", "/api/menu/**", "/api/categories", "/api/categories/**").permitAll()
                // Admin
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Any logged-in user, including the admin
                .requestMatchers("/api/auth/me", "/api/profile/password").authenticated()
                // Customers only. Admins cannot shop (gap G22).
                .requestMatchers("/api/cart", "/api/cart/**", "/api/orders", "/api/orders/**",
                        "/api/addresses", "/api/addresses/**", "/api/profile", "/api/profile/**",
                        "/api/payments/**").hasRole("CUSTOMER")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth
                .bearerTokenResolver(bearerTokenResolver())
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                .authenticationEntryPoint(authErrors)
                .accessDeniedHandler(authErrors))
            .exceptionHandling(e -> e
                .authenticationEntryPoint(authErrors)
                .accessDeniedHandler(authErrors))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * Reads the bearer token, except on the public auth endpoints. Without this, a stale
     * token left in the client would make login or refresh fail with 401.
     */
    private static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return request -> TOKENLESS_AUTH_PATHS.contains(request.getRequestURI()) ? null : delegate.resolve(request);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Location"));
        // Tokens travel in the Authorization header, not cookies.
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
