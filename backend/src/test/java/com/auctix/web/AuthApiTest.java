package com.auctix.web;

import com.auctix.TestDataFactory;
import com.auctix.domain.Role;
import com.auctix.dto.auth.LoginRequest;
import com.auctix.dto.auth.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestDataFactory data;

    @Test
    @DisplayName("Registration returns a JWT and never echoes the password")
    void registersAndIssuesToken() throws Exception {
        RegisterRequest request = new RegisterRequest("New Bidder", "new.bidder@auctix.io",
                "Password123!", Role.BIDDER);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("new.bidder@auctix.io"))
                .andExpect(jsonPath("$.user.role").value("BIDDER"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("A duplicate e-mail is rejected with EMAIL_ALREADY_USED")
    void rejectsDuplicateEmail() throws Exception {
        data.user("duplicate@auctix.io", Role.BIDDER);
        RegisterRequest request = new RegisterRequest("Someone Else", "duplicate@auctix.io",
                "Password123!", Role.BIDDER);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    @DisplayName("Weak passwords fail bean validation before reaching the service")
    void rejectsWeakPasswords() throws Exception {
        RegisterRequest request = new RegisterRequest("Weak Password", "weak@auctix.io", "short", null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Wrong credentials return INVALID_CREDENTIALS, not a stack trace")
    void rejectsWrongPassword() throws Exception {
        data.user("login@auctix.io", Role.BIDDER);
        LoginRequest request = new LoginRequest("login@auctix.io", "WrongPassword1");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("Protected endpoints reject anonymous callers")
    void protectsPrivateEndpoints() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("Public endpoints stay open to visitors")
    void allowsPublicEndpoints() throws Exception {
        mockMvc.perform(get("/api/auctions"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/system/time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epochMillis").isNumber());
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").isNotEmpty());
    }
}
