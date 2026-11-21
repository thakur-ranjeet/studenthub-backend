package com.example.demo.security;

import java.io.IOException;
import java.security.Key;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Verifies the JWT itself (signature + expiry), so this service stays safe even if someone
// calls it directly instead of through the gateway.
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

	@Value("${jwt.secret}")
	private String secret;

	private Key key;

	@PostConstruct
	void init() {
		key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/v1/shop") || "OPTIONS".equalsIgnoreCase(request.getMethod());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header == null || !header.startsWith("Bearer ")) {
			unauthorized(response, "Missing authorization header");
			return;
		}
		try {
			Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(header.substring(7)).getBody();
			request.setAttribute("username", claims.getSubject());
			request.setAttribute("role", claims.get("role", String.class));
		} catch (JwtException | IllegalArgumentException e) {
			unauthorized(response, "Invalid or expired token");
			return;
		}
		chain.doFilter(request, response);
	}

	private void unauthorized(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("application/json");
		response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}");
	}
}
