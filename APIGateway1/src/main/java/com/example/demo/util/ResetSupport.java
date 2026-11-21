package com.example.demo.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

// Small, framework-free helpers for the password reset feature (easy to unit test).
public final class ResetSupport {

	public static final int MIN_PASSWORD = 6; // same rule as registration
	public static final int MAX_PASSWORD_BYTES = 72; // BCrypt only uses the first 72 bytes

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}$");

	private ResetSupport() {
	}

	/** 32 random bytes (256 bits) as URL-safe text, 43 characters. This is what goes into the e-mailed link. */
	public static String newToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** Only this hash is stored. If the database leaks, the hashes cannot be used as reset links. */
	public static String sha256Hex(String text) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	/** Trimmed and lower-case, or null when nothing was entered. */
	public static String normalizeEmail(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		return raw.trim().toLowerCase(Locale.ROOT);
	}

	public static boolean isValidEmail(String email) {
		return email != null && email.length() <= 254 && EMAIL.matcher(email).matches();
	}

	/** null when the password is acceptable, otherwise a message for the user. */
	public static String passwordProblem(String password) {
		if (password == null || password.length() < MIN_PASSWORD) {
			return "The password must be at least " + MIN_PASSWORD + " characters";
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
			return "The password is too long (at most " + MAX_PASSWORD_BYTES + " characters)";
		}
		return null;
	}
}
