package com.example.demo.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.Claims;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // Set secret key directly for unit testing
        ReflectionTestUtils.setField(jwtUtil, "secret", "dGVzdC1vbmx5LW5vdC1hLXJlYWwtc2VjcmV0LWtleS0wMTIzNDU2Nzg5");
        }

    @Test
    void testGenerateTokenAndExtractClaims() {
        String token = jwtUtil.generateToken("john_doe", "ROLE_ADMIN");

        assertNotNull(token);

        Claims claims = jwtUtil.extractAllClaims(token);
        assertEquals("john_doe", claims.getSubject());
        assertEquals("ROLE_ADMIN", claims.get("role", String.class));
    }

    @Test
    void testValidateToken_Success() {
        String token = jwtUtil.generateToken("john_doe", "ROLE_STUDENT");

        assertDoesNotThrow(() -> jwtUtil.validateToken(token));
    }
}