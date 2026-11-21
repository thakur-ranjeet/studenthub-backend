package com.example.demo.filter;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

    private final RouteValidator validator;
    private final JwtUtil jwtUtil;

    public AuthenticationFilter(RouteValidator validator, JwtUtil jwtUtil) {
        super(Config.class);
        this.validator = validator;
        this.jwtUtil = jwtUtil;
    }

    public static class Config {
        // Drop configuration properties here if needed later
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            if (validator.isSecured.test(request)) {
                if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    return onError(exchange, "Missing authorization header", HttpStatus.UNAUTHORIZED);
                }

                String authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    authHeader = authHeader.substring(7);
                }

                try {
                    // 1. Validate token
                    jwtUtil.validateToken(authHeader);

                    // 2. Extract claims directly using your existing JwtUtil method
                    io.jsonwebtoken.Claims claims = jwtUtil.extractAllClaims(authHeader); // or jwtUtil.getAllClaimsFromToken(authHeader)
                    String userRole = claims.get("role", String.class);

                    String path = request.getURI().getPath();

                    boolean isAdmin = "ROLE_ADMIN".equals(userRole);

                    // Student records, and the course endpoint that returns them, are admin-only
                    boolean studentData = path.contains("/api/v1/students")
                            || path.contains("/api/v1/courses/courseswithstudent");
                    if (studentData && !isAdmin) {
                        return onError(exchange, "Unauthorized! Only Administrators can access Student Records.", HttpStatus.FORBIDDEN);
                    }

                    // Anyone signed in may READ courses, but only administrators may add or change them
                    HttpMethod method = request.getMethod();
                    boolean isWrite = !(HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method)
                            || HttpMethod.OPTIONS.equals(method));
                    if (isWrite && path.contains("/api/v1/courses") && !isAdmin) {
                        return onError(exchange, "Only Administrators can change courses.", HttpStatus.FORBIDDEN);
                    }

                } catch (Exception e) {
                    logger.error("JWT validation failed: {}", e.getMessage());
                    return onError(exchange, "Unauthorized access to microservice: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
                }
            }
            return chain.filter(exchange);
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        logger.info("❌ [Gateway Filter Blocked]: {}", err);

        String jsonResponse = String.format(
                """
                {
                  "timestamp": "%s",
                  "status": %d,
                  "error": "%s",
                  "message": "%s",
                  "path": "%s"
                }""",
                Instant.now().toString(),
                httpStatus.value(),
                httpStatus.getReasonPhrase(),
                err.replace("\"", "\\\""),
                exchange.getRequest().getURI().getPath()
            );

        DataBufferFactory bufferFactory = response.bufferFactory();
        DataBuffer buffer = bufferFactory.wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }
}
