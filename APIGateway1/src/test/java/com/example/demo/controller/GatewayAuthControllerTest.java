package com.example.demo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.example.demo.filter.JwtUtil;
import com.example.demo.model.UserCredentials;
import com.example.demo.repository.UserCredentialsRepository;

@WebFluxTest(GatewayAuthController.class)
class GatewayAuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private UserCredentialsRepository repository;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @Test
    void testRegister_Success() {
        when(repository.findByUsername("john")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed_pass");

        UserCredentials savedUser = new UserCredentials();
        savedUser.setId(101L);
        savedUser.setUsername("john");
        when(repository.save(any())).thenReturn(savedUser);

        String json = """
            {
                "username": "john",
                "password": "password123",
                "role": "ROLE_STUDENT"
            }
            """;

        webTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("User registered successfully with ID: 101");
    }

    @Test
    void testRegister_UserAlreadyExists() {
        UserCredentials existingUser = new UserCredentials();
        existingUser.setUsername("john");
        when(repository.findByUsername("john")).thenReturn(Optional.of(existingUser));

        String json = """
            {
                "username": "john",
                "password": "password123"
            }
            """;

        webTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Username already exists!");
    }

    @Test
    void testLogin_Success() {
        UserCredentials user = new UserCredentials();
        user.setUsername("john");
        user.setPassword("hashed_pass");
        user.setRole("ROLE_STUDENT");

        when(repository.findByUsername("john")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed_pass")).thenReturn(true);
        when(jwtUtil.generateToken("john", "ROLE_STUDENT")).thenReturn("mocked_jwt_token");

        String json = """
            {
                "username": "john",
                "password": "password123"
            }
            """;

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.token").isEqualTo("mocked_jwt_token");
    }

    @Test
    void testLogin_Unauthorized() {
        when(repository.findByUsername("john")).thenReturn(Optional.empty());

        String json = """
            {
                "username": "john",
                "password": "wrongpassword"
            }
            """;

        webTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Invalid username or password");
    }
}