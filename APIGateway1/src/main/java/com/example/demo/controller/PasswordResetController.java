package com.example.demo.controller;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.ForgotRequest;
import com.example.demo.dto.ResetRequest;
import com.example.demo.service.PasswordResetService;
import com.example.demo.service.ResetException;
import com.example.demo.util.ResetRateLimiter;

/**
 * Forgot password, in two steps. Both endpoints are open (no JWT), listed in RouteValidator.
 *  POST /api/v1/auth/forgot-password  { identifier }        -> always the same answer
 *  POST /api/v1/auth/reset-password   { token, password }
 */
@RestController
@RequestMapping("/api/v1/auth")
public class PasswordResetController {

	private static final long WINDOW_MS = 15 * 60 * 1000L;
	// the same words whether or not the account exists, so this cannot be used to find out who has an account
	private static final String SENT = "If an account matches, a password reset link is on its way. Check your inbox and your spam folder.";

	private final PasswordResetService service;
	private final ExecutorService executor;
	private final ResetRateLimiter forgotPerIp = new ResetRateLimiter(WINDOW_MS);
	private final ResetRateLimiter forgotPerAccount = new ResetRateLimiter(WINDOW_MS);
	private final ResetRateLimiter resetPerIp = new ResetRateLimiter(WINDOW_MS);

	public PasswordResetController(PasswordResetService service, @Qualifier("resetExecutor") ExecutorService executor) {
		this.service = service;
		this.executor = executor;
	}

	@PostMapping("/forgot-password")
	public ResponseEntity<Map<String, String>> forgot(@RequestBody ForgotRequest request, ServerHttpRequest http) {
		String identifier = request == null || request.identifier() == null ? "" : request.identifier().trim();
		if (identifier.isEmpty() || identifier.length() > 254) {
			return ResponseEntity.badRequest().body(Map.of("error", "Enter your username or e-mail address"));
		}
		if (!forgotPerIp.allow(clientIp(http), 10)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", "Too many requests. Please try again in a few minutes."));
		}
		// at most 3 links per account per 15 minutes (stops someone flooding a victim's inbox). Over the limit: same answer, no e-mail.
		if (forgotPerAccount.allow(identifier.toLowerCase(Locale.ROOT), 3)) {
			executor.execute(() -> service.sendResetLink(identifier)); // background: same response time for every account
		}
		return ResponseEntity.ok(Map.of("message", SENT));
	}

	@PostMapping("/reset-password")
	public ResponseEntity<Map<String, String>> reset(@RequestBody ResetRequest request, ServerHttpRequest http) {
		if (!resetPerIp.allow(clientIp(http), 20)) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", "Too many attempts. Please try again in a few minutes."));
		}
		try {
			service.resetPassword(request == null ? null : request.token(), request == null ? null : request.password());
			return ResponseEntity.ok(Map.of("message", "Your password has been updated. You can sign in now."));
		} catch (ResetException e) {
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	private static String clientIp(ServerHttpRequest http) {
		return http.getRemoteAddress() == null ? "unknown" : http.getRemoteAddress().getHostString();
	}
}
