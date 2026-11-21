package com.example.demo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class ResetSupportTest {

	@Test
	void tokensAreLongAndDifferentEveryTime() {
		String a = ResetSupport.newToken();
		String b = ResetSupport.newToken();
		assertEquals(43, a.length());
		assertNotEquals(a, b);
		assertTrue(a.matches("[A-Za-z0-9_-]+"));
	}

	@Test
	void hashIsStableAndHexOf64Characters() {
		assertEquals(ResetSupport.sha256Hex("abc"), ResetSupport.sha256Hex("abc"));
		assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ResetSupport.sha256Hex("abc"));
		assertEquals(64, ResetSupport.sha256Hex(ResetSupport.newToken()).length());
	}

	@Test
	void emailIsTrimmedLowerCasedAndChecked() {
		assertNull(ResetSupport.normalizeEmail("   "));
		assertNull(ResetSupport.normalizeEmail(null));
		assertEquals("ronny@example.com", ResetSupport.normalizeEmail("  Ronny@Example.COM "));
		assertTrue(ResetSupport.isValidEmail("ronny@example.com"));
		assertFalse(ResetSupport.isValidEmail("ronny@"));
		assertFalse(ResetSupport.isValidEmail("ronny example@x.com"));
		assertFalse(ResetSupport.isValidEmail("no-at-sign.com"));
	}

	@Test
	void passwordRules() {
		assertNotNull(ResetSupport.passwordProblem("12345"));
		assertNull(ResetSupport.passwordProblem("123456"));
		assertNotNull(ResetSupport.passwordProblem("x".repeat(73)));
		assertNull(ResetSupport.passwordProblem("x".repeat(72)));
		assertNotNull(ResetSupport.passwordProblem(null));
	}

	@Test
	void rateLimiterAllowsNThenBlocksThenRecovers() {
		AtomicLong now = new AtomicLong(1_000);
		ResetRateLimiter limiter = new ResetRateLimiter(60_000, now::get);
		assertTrue(limiter.allow("a", 2));
		assertTrue(limiter.allow("a", 2));
		assertFalse(limiter.allow("a", 2));
		assertTrue(limiter.allow("b", 2)); // another key has its own budget
		now.addAndGet(60_001);
		assertTrue(limiter.allow("a", 2)); // the window has passed
	}
}
