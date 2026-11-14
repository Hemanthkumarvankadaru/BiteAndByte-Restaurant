package com.biteandbyte.restaurant.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.biteandbyte.restaurant.support.AuthTestSupport;
import com.biteandbyte.restaurant.support.AuthTestSupport.TestUser;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileAndAddressTest {

    @Autowired private MockMvc mockMvc;

    private TestUser customer;

    @BeforeEach
    void setUp() throws Exception {
        customer = AuthTestSupport.registerCustomer(mockMvc);
    }

    // ---- Profile ----

    @Test
    void customerViewsAndUpdatesTheirProfile() throws Exception {
        mockMvc.perform(get("/api/profile").header("Authorization", customer.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(customer.username()))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        String email = "New_" + AuthTestSupport.uniqueSuffix() + "@Example.com";
        String phone = AuthTestSupport.uniquePhone();
        updateProfile(customer, email, phone)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Priya Sharma"))
                .andExpect(jsonPath("$.data.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.data.phone").value(phone))
                .andExpect(jsonPath("$.data.username").value(customer.username()));

        // Saving the same values again is not a conflict with yourself.
        updateProfile(customer, email, phone).andExpect(status().isOk());
    }

    @Test
    void profileUpdateRejectsSomeoneElsesEmailOrPhone() throws Exception {
        TestUser other = AuthTestSupport.registerCustomer(mockMvc);

        updateProfile(customer, other.email().toUpperCase(), AuthTestSupport.uniquePhone())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_TAKEN"));
        updateProfile(customer, customer.email(), other.phone())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PHONE_TAKEN"));
        updateProfile(customer, "not-an-email", "12345")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    // ---- Addresses ----

    @Test
    void exactlyOneAddressIsTheDefault() throws Exception {
        long home = firstAddressId(customer);

        long office = idOf(createAddress(customer, "Office", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.defaultAddress").value(false)));
        long parents = idOf(createAddress(customer, "Parents", true)
                .andExpect(jsonPath("$.data.defaultAddress").value(true)));

        listAddresses(customer)
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value(parents))
                .andExpect(jsonPath("$.data[?(@.defaultAddress == true)].id", contains((int) parents)));

        // Making the office the default clears the flag on the others.
        mockMvc.perform(put("/api/addresses/" + office).header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(addressJson("Office", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultAddress").value(true));
        listAddresses(customer).andExpect(jsonPath("$.data[?(@.defaultAddress == true)].id", contains((int) office)));

        // Deleting the default promotes the oldest remaining one.
        mockMvc.perform(delete("/api/addresses/" + office).header("Authorization", customer.bearer()))
                .andExpect(status().isOk());
        listAddresses(customer)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.defaultAddress == true)].id", contains((int) home)));
    }

    @Test
    void customersCannotTouchSomeoneElsesAddress() throws Exception {
        TestUser other = AuthTestSupport.registerCustomer(mockMvc);
        long othersAddress = firstAddressId(other);

        mockMvc.perform(put("/api/addresses/" + othersAddress).header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(addressJson("Mine now", true)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/addresses/" + othersAddress).header("Authorization", customer.bearer()))
                .andExpect(status().isNotFound());
        listAddresses(other)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].label").value("Home"));
    }

    @Test
    void validatesAddresses() throws Exception {
        mockMvc.perform(post("/api/addresses").header("Authorization", customer.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"street\":\"1 Road\",\"city\":\"Hyderabad\",\"state\":\"Telangana\",\"pincode\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("pincode"));
    }

    // ---- Helpers ----

    private ResultActions updateProfile(TestUser user, String email, String phone) throws Exception {
        return mockMvc.perform(put("/api/profile").header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName": "Priya Sharma", "email": "%s", "phone": "%s", "age": 29, "gender": "FEMALE"}
                        """.formatted(email, phone)));
    }

    private ResultActions createAddress(TestUser user, String label, Boolean makeDefault) throws Exception {
        return mockMvc.perform(post("/api/addresses").header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(addressJson(label, makeDefault)));
    }

    private ResultActions listAddresses(TestUser user) throws Exception {
        return mockMvc.perform(get("/api/addresses").header("Authorization", user.bearer())).andExpect(status().isOk());
    }

    private long firstAddressId(TestUser user) throws Exception {
        return ((Number) JsonPath.read(listAddresses(user).andReturn().getResponse().getContentAsString(),
                "$.data[0].id")).longValue();
    }

    private static long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private static String addressJson(String label, Boolean makeDefault) {
        return """
                {"label": "%s", "street": "Plot 41, Hitech City Main Road", "city": "Hyderabad",
                 "state": "Telangana", "pincode": "500081", "landmark": "Opposite Cyber Towers"%s}
                """.formatted(label, makeDefault == null ? "" : ", \"defaultAddress\": " + makeDefault);
    }
}
