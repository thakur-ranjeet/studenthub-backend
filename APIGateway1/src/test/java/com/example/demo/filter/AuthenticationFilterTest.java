package com.example.demo.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import io.jsonwebtoken.impl.DefaultClaims;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AuthenticationFilterTest {

    @Mock
    private RouteValidator validator;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private GatewayFilterChain filterChain;

    @Mock
    private Predicate<ServerHttpRequest> isSecuredPredicate;

    private AuthenticationFilter authenticationFilter;

    @BeforeEach
    void setUp() {
        // Point the predicate field to our mock predicate
        validator.isSecured = isSecuredPredicate;
        authenticationFilter = new AuthenticationFilter(validator, jwtUtil);
    }

    @Test
    void testFilter_UnsecuredRoute_PassesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8083/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(isSecuredPredicate.test(any())).thenReturn(false);
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());

        Mono<Void> result = authenticationFilter.apply(new AuthenticationFilter.Config()).filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void testFilter_SecuredRoute_MissingHeader_ReturnsUnauthorized() {
        MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8083/api/v1/students").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(isSecuredPredicate.test(any())).thenReturn(true);

        Mono<Void> result = authenticationFilter.apply(new AuthenticationFilter.Config()).filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void testFilter_SecuredRoute_ValidToken_Success() {
        MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8083/api/v1/students")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid_token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(isSecuredPredicate.test(any())).thenReturn(true);
        doNothing().when(jwtUtil).validateToken("valid_token");

        DefaultClaims claims = new DefaultClaims();
        claims.put("role", "ROLE_ADMIN");
        when(jwtUtil.extractAllClaims("valid_token")).thenReturn(claims);
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());

        Mono<Void> result = authenticationFilter.apply(new AuthenticationFilter.Config()).filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void testFilter_SecuredRoute_ForbiddenRole_ReturnsForbidden() {
        MockServerHttpRequest request = MockServerHttpRequest.get("http://localhost:8083/api/v1/students")
                .header(HttpHeaders.AUTHORIZATION, "Bearer student_token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(isSecuredPredicate.test(any())).thenReturn(true);
        doNothing().when(jwtUtil).validateToken("student_token");

        DefaultClaims claims = new DefaultClaims();
        claims.put("role", "ROLE_STUDENT");
        when(jwtUtil.extractAllClaims("student_token")).thenReturn(claims);

        Mono<Void> result = authenticationFilter.apply(new AuthenticationFilter.Config()).filter(exchange, filterChain);

        StepVerifier.create(result).verifyComplete();
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }
}