package com.example.demo.dto;

// The signed-in user, taken from the verified JWT. The token is forwarded to the Order service,
// which verifies it again and answers only for this user.
public record UserContext(String username, String role, String token) {

	public boolean isAdmin() {
		return "ROLE_ADMIN".equals(role);
	}
}
