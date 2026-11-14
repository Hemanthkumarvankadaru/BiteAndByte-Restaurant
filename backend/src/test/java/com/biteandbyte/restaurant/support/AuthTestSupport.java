package com.biteandbyte.restaurant.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Registers and logs in users through the real endpoints. Test contexts share one in-memory
 * database, so every customer gets a unique username, email and phone.
 */
public final class AuthTestSupport {

    public static final String PASSWORD = "Biryani123";

    /** Tokens and identity of a user created for a test. */
    public record TestUser(String username, String email, String phone, String accessToken, String refreshToken) {
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    public static String uniqueSuffix() {
        return Long.toString(System.nanoTime(), 36) + ThreadLocalRandom.current().nextInt(1000);
    }

    public static String uniquePhone() {
        return "9" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
    }

    public static String registerJson(String username, String email, String phone, String password) {
        return """
                {
                  "username": "%s",
                  "email": "%s",
                  "phone": "%s",
                  "password": "%s",
                  "fullName": "Test Customer",
                  "age": 30,
                  "gender": "FEMALE",
                  "address": {
                    "label": "Home",
                    "street": "8-2-293 Road No. 12, Banjara Hills",
                    "city": "Hyderabad",
                    "state": "Telangana",
                    "pincode": "500034",
                    "landmark": "Near the park"
                  }
                }
                """.formatted(username, email, phone, password);
    }

    public static TestUser registerCustomer(MockMvc mockMvc) throws Exception {
        String suffix = uniqueSuffix();
        String username = "cust_" + suffix;
        String email = "cust_" + suffix + "@example.com";
        String phone = uniquePhone();
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, email, phone, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        return fromAuthResponse(username, email, phone, result);
    }

    public static TestUser login(MockMvc mockMvc, String usernameOrEmail, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(usernameOrEmail, password)))
                .andExpect(status().isOk())
                .andReturn();
        return fromAuthResponse(usernameOrEmail, null, null, result);
    }

    public static TestUser loginAdmin(MockMvc mockMvc) throws Exception {
        return login(mockMvc, "admin", "admin");
    }

    public static String loginJson(String usernameOrEmail, String password) {
        return "{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}".formatted(usernameOrEmail, password);
    }

    public static String refreshJson(String refreshToken) {
        return "{\"refreshToken\":\"%s\"}".formatted(refreshToken);
    }

    private static TestUser fromAuthResponse(String username, String email, String phone, MvcResult result)
            throws Exception {
        String body = result.getResponse().getContentAsString();
        return new TestUser(username, email, phone,
                JsonPath.read(body, "$.data.accessToken"),
                JsonPath.read(body, "$.data.refreshToken"));
    }

    private AuthTestSupport() {
    }
}
