package com.example.demo.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.demo.model.PasswordResetToken;
import com.example.demo.model.UserCredentials;
import com.example.demo.repository.PasswordResetTokenRepository;
import com.example.demo.repository.UserCredentialsRepository;
import com.example.demo.util.ResetSupport;

@Service
public class PasswordResetService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
	private static final String BAD_LINK = "This reset link is not valid or has expired. Please request a new one.";

	private final UserCredentialsRepository users;
	private final PasswordResetTokenRepository tokens;
	private final PasswordEncoder encoder;
	private final MailService mail;
	private final ExecutorService executor;
	private final long minutes;
	private final String frontendUrl;

	public PasswordResetService(UserCredentialsRepository users, PasswordResetTokenRepository tokens, PasswordEncoder encoder,
			MailService mail, @Qualifier("resetExecutor") ExecutorService executor,
			@Value("${app.reset.token-minutes:30}") long minutes,
			@Value("${app.frontend.url:http://localhost:3000}") String frontendUrl) {
		this.users = users;
		this.tokens = tokens;
		this.encoder = encoder;
		this.mail = mail;
		this.executor = executor;
		this.minutes = minutes;
		this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
	}

	/**
	 * Step 1. Runs in the background and never tells the caller whether the account exists.
	 * Accounts without an e-mail address cannot be reached, so they get nothing.
	 */
	public void sendResetLink(String identifier) {
		try {
			String id = identifier.trim();
			Optional<UserCredentials> found = users.findByUsername(id);
			if (found.isEmpty() && id.contains("@")) {
				found = users.findByEmail(id.toLowerCase(Locale.ROOT));
			}
			if (found.isEmpty() || found.get().getEmail() == null || found.get().getEmail().isBlank()) {
				log.info("Password reset requested for an account that does not exist or has no e-mail address");
				return;
			}
			UserCredentials user = found.get();

			tokens.deleteByUserId(user.getId()); // a new request replaces any older link
			tokens.deleteExpiredBefore(Instant.now().minus(1, ChronoUnit.DAYS)); // tidy up old rows

			String rawToken = ResetSupport.newToken();
			tokens.save(new PasswordResetToken(ResetSupport.sha256Hex(rawToken), user.getId(), Instant.now().plus(minutes, ChronoUnit.MINUTES)));

			// the token sits after the # so it is never sent to a server and never shows up in a Referer header
			String link = frontendUrl + "/#/reset-password/" + rawToken;
			mail.sendResetLink(user.getEmail(), user.getUsername(), link, minutes);
		} catch (RuntimeException e) {
			log.warn("Could not process a password reset request: {}", e.getClass().getSimpleName());
		}
	}

	/** Step 2. The link must exist, be unused and not expired. It can be used only once. */
	public void resetPassword(String rawToken, String newPassword) {
		String problem = ResetSupport.passwordProblem(newPassword);
		if (problem != null) {
			throw new ResetException(problem);
		}
		if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
			throw new ResetException(BAD_LINK);
		}
		PasswordResetToken token = tokens.findByTokenHash(ResetSupport.sha256Hex(rawToken.trim())).orElseThrow(() -> new ResetException(BAD_LINK));
		if (tokens.markUsed(token.getId(), Instant.now()) != 1) { // expired, or already used (also by a parallel request)
			throw new ResetException(BAD_LINK);
		}
		UserCredentials user = users.findById(token.getUserId()).orElseThrow(() -> new ResetException(BAD_LINK));

		user.setPassword(encoder.encode(newPassword));
		users.save(user);
		tokens.deleteByUserId(user.getId()); // also cancels any other link that was still open

		if (user.getEmail() != null && !user.getEmail().isBlank()) {
			String to = user.getEmail();
			String name = user.getUsername();
			executor.execute(() -> mail.sendPasswordChanged(to, name));
		}
	}
}
