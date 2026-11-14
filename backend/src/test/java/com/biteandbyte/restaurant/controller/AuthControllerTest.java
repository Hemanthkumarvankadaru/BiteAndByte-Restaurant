package com.biteandbyte.restaurant.controller;

import static com.biteandbyte.restaurant.support.AuthTestSupport.PASSWORD;
import static com.biteandbyte.restaurant.support.AuthTestSupport.loginJson;
import static com.biteandbyte.restaurant.support.AuthTestSupport.refreshJson;
import static com.biteandbyte.restaurant.support.AuthTestSupport.registerJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.biteandbyte.restaurant.config.JwtProperties;
import com.biteandbyte.restaurant.model.Address;
import com.biteandbyte.restaurant.model.User;
import com.biteandbyte.restaurant.repository.AddressRepository;
import com.biteandbyte.restaurant.repository.UserRepository;
import com.biteandbyte.restaurant.support.AuthTestSupport;
import com.biteandbyte.restaurant.support.AuthTestSupport.TestUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private AddressRepository addresses;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private JwtProperties jwtProperties;

    // ---- Register ----

    @Test
    void registerCreatesCustomerWithDefaultAddressAndReturnsTokens() throws Exception {
        String suffix = AuthTestSupport.uniqueSuffix();
        String phone = AuthTestSupport.uniquePhone();

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Priya_" + suffix, "Priya_" + suffix + "@Example.com", phone, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.user.username").value("priya_" + suffix.toLowerCase()))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());

        User saved = users.findByUsernameIgnoreCase("priya_" + suffix).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("priya_" + suffix.toLowerCase() + "@example.com");
        assertThat(saved.getPasswordHash()).startsWith("$2");
        List<Address> saved_addresses = addresses.findByUserIdOrderByDefaultAddressDescIdAsc(saved.getId());
        assertThat(saved_addresses).hasSize(1);
        assertThat(saved_addresses.get(0).isDefaultAddress()).isTrue();
        assertThat(saved_addresses.get(0).getPincode()).isEqualTo("500034");
    }

    @Test
    void registerRejectsDuplicateEmailUsernameAndPhone() throws Exception {
        TestUser existing = AuthTestSupport.registerCustomer(mockMvc);
        String fresh = AuthTestSupport.uniqueSuffix();

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("new_" + fresh, existing.email().toUpperCase(), AuthTestSupport.uniquePhone(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_TAKEN"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(existing.username(), fresh + "@example.com", AuthTestSupport.uniquePhone(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USERNAME_TAKEN"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("new_" + fresh, fresh + "@example.com", existing.phone(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PHONE_TAKEN"));
    }

    @Test
    void registerValidatesFields() throws Exception {
        String suffix = AuthTestSupport.uniqueSuffix();
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("u_" + suffix, "not-an-email", "12345", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field",
                        Matchers.containsInAnyOrder("email", "phone", "password")));
    }

    @Test
    void registerRejectsUnknownGender() throws Exception {
        String body = registerJson("g_" + AuthTestSupport.uniqueSuffix(), "g@example.com",
                AuthTestSupport.uniquePhone(), PASSWORD).replace("\"FEMALE\"", "\"ROBOT\"");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    // ---- Login ----

    @Test
    void loginWorksWithUsernameOrEmailInAnyCase() throws Exception {
        TestUser customer = AuthTestSupport.registerCustomer(mockMvc);

        AuthTestSupport.login(mockMvc, customer.username().toUpperCase(), PASSWORD);
        AuthTestSupport.login(mockMvc, customer.email().toUpperCase(), PASSWORD);
    }

    @Test
    void loginRejectsWrongPasswordAndUnknownUserTheSameWay() throws Exception {
        TestUser customer = AuthTestSupport.registerCustomer(mockMvc);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(customer.username(), "WrongPass1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("nobody_" + AuthTestSupport.uniqueSuffix(), "WrongPass1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void seededAdminCanLogIn() throws Exception {
        TestUser admin = AuthTestSupport.loginAdmin(mockMvc);

        mockMvc.perform(get("/api/auth/me").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    void loginIgnoresAStaleBearerToken() throws Exception {
        TestUser customer = AuthTestSupport.registerCustomer(mockMvc);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer stale.or.garbage")
                        .content(loginJson(customer.username(), PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void disabledAccountCannotLogIn() throws Exception {
        TestUser customer = AuthTestSupport.registerCustomer(mockMvc);
        User user = users.findByUsernameIgnoreCase(customer.username()).orElseThrow();
        user.setActive(false);
        users.save(user);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(customer.username(), PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
    }

    // ---- Access token checks ----

    @Test
    void meRequiresAValidToken() throws Exception {
        TestUser customer = AuthTestSupport.registerCustomer(mockMvc);

        mockMvc.perform(get("/api/auth/me").header("Authorization", customer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(customer.username()));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Login required"));

        String tampered = customer.accessToken().substring(0, customer.accessToken().length() - 3) + "abc";
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("invalid or expired")));
    }

    @Test
    void expiredAccessTokenIsRejected() throws Exception {
        Instant issued = Instant.now().minus(Duration.ofHours(1));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .issuedAt(issued)
                .expiresAt(issued.plus(Duration.ofMinutes(15)))
                .subject("1")
                .claim("roles", List.of("ADMIN"))
                .build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    // ---- Refresh and logout ----

    @Test
    void refreshRotatesTokensAndDetectsReuse() throws Exception {
        TestUser customer = AuthTestSupport.registerCustomer(mockMvc);

        String body = mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(customer.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String newRefresh = com.jayway.jsonpath.JsonPath.read(body, "$.data.refreshToken");
        assertThat(newRefresh).isNotEqualTo(customer.refreshToken());

        // Reusing the old token fails...
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(customer.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));

        // ...and also kills the new one, because reuse suggests the old token was stolen.
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(newRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRefreshTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson("definitely-not-a-real-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutRevokesTheRefreshTokenAndIsIdempotent() throws Exception {
        TestUser customer = AuthTestSupport.registerCustomer(mockMvc);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                            .content(refreshJson(customer.refreshToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Logged out"));
        }

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(customer.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    // ---- Password change ----

    @Test
    void changePasswordChecksCurrentPasswordAndSignsOutOtherSessions() throws Exception {
        TestUser customer = AuthTestSupport.registerCustomer(mockMvc);

        mockMvc.perform(put("/api/profile/password").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"WrongPass1\",\"newPassword\":\"NewBiryani456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("WRONG_PASSWORD"));

        mockMvc.perform(put("/api/profile/password").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SAME_PASSWORD"));

        mockMvc.perform(put("/api/profile/password").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"NewBiryani456\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(customer.username(), PASSWORD)))
                .andExpect(status().isUnauthorized());
        AuthTestSupport.login(mockMvc, customer.username(), "NewBiryani456");

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(customer.refreshToken())))
                .andExpect(status().isUnauthorized());
    }
}
