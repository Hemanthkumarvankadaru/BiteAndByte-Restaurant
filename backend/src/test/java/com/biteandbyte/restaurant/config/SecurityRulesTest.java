package com.biteandbyte.restaurant.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.biteandbyte.restaurant.support.AuthTestSupport;
import com.biteandbyte.restaurant.support.AuthTestSupport.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Checks the access rules per path prefix. A blocked request gets 401 or 403 from the security
 * filters; an allowed one reaches the controller. The business behaviour behind each prefix is
 * tested in the controller tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityRulesTest {

    @Autowired private MockMvc mockMvc;

    private TestUser customer;
    private TestUser admin;

    @BeforeEach
    void setUp() throws Exception {
        customer = AuthTestSupport.registerCustomer(mockMvc);
        admin = AuthTestSupport.loginAdmin(mockMvc);
    }

    @Test
    void menuAndCategoriesArePublic() throws Exception {
        mockMvc.perform(get("/api/menu")).andExpect(status().isOk());
        mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
        // Reaches the controller (404 from the service), not the security filter (401).
        mockMvc.perform(get("/api/menu/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void customerAreasNeedLogin() throws Exception {
        for (String path : new String[] {"/api/cart", "/api/orders", "/api/addresses", "/api/profile"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        }
    }

    @Test
    void customersCannotUseAdminEndpoints() throws Exception {
        for (String path : new String[] {"/api/admin/dashboard", "/api/admin/orders", "/api/admin/menu-items",
                "/api/admin/categories", "/api/admin/reports/revenue"}) {
            mockMvc.perform(get(path).header("Authorization", customer.bearer()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        }
    }

    @Test
    void adminsCannotShop() throws Exception {
        for (String path : new String[] {"/api/cart", "/api/orders", "/api/addresses", "/api/profile"}) {
            mockMvc.perform(get(path).header("Authorization", admin.bearer()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void eachRoleReachesItsOwnArea() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", admin.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cart").header("Authorization", customer.bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/addresses").header("Authorization", customer.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousAdminRequestIsUnauthorizedNotForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
    }
}
