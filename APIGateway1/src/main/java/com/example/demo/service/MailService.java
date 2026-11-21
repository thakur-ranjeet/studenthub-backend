package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends the plain-text e-mails. Call it from a background thread (it waits for the mail server).
 * With app.mail.enabled=false nothing is sent and the reset link is written to the console instead.
 * That is for DEVELOPMENT only: a link in a log file is a password reset for anyone who can read the log.
 */
@Service
public class MailService {

	private static final Logger log = LoggerFactory.getLogger(MailService.class);

	private final ObjectProvider<JavaMailSender> sender;
	private final boolean enabled;
	private final String from;

	public MailService(ObjectProvider<JavaMailSender> sender, @Value("${app.mail.enabled:false}") boolean enabled,
			@Value("${app.mail.from:no-reply@studenthub.local}") String from) {
		this.sender = sender;
		this.enabled = enabled;
		this.from = from;
	}

	public void sendResetLink(String to, String username, String link, long minutes) {
		String text = "Hi " + username + ",\n\n"
				+ "We received a request to reset your StudentHub password. Open this link to choose a new one:\n\n"
				+ link + "\n\n"
				+ "The link works once and expires in " + minutes + " minutes.\n"
				+ "If you did not ask for this, you can ignore this e-mail. Your password will not change.\n";
		send(to, "Reset your StudentHub password", text, "Reset link: " + link);
	}

	public void sendPasswordChanged(String to, String username) {
		String text = "Hi " + username + ",\n\n"
				+ "The password of your StudentHub account was just changed.\n"
				+ "If this was you, there is nothing more to do. If it was not you, reset your password again and contact the administrator.\n";
		send(to, "Your StudentHub password was changed", text, null);
	}
	
	public void sendSupportQuery(String userIdentifier, String replyEmail, String subject, String messageText) {
		String body = "New Help / Support Query on StudentHub\n\n"
				+ "From User: " + userIdentifier + "\n"
				+ "Reply-To Email: " + replyEmail + "\n"
				+ "Subject: " + subject + "\n\n"
				+ "Message:\n" + messageText + "\n";
		send(from, "[StudentHub Help] " + subject, body, "Support query from " + replyEmail);
	}

	private void send(String to, String subject, String text, String devNote) {
		if (!enabled) {
			log.warn("MAIL IS OFF (app.mail.enabled=false), nothing was sent: '{}'. DEVELOPMENT ONLY. {}", subject,
					devNote == null ? "" : devNote);
			return;
		}
		JavaMailSender mailSender = sender.getIfAvailable();
		if (mailSender == null) {
			log.error("app.mail.enabled=true but spring.mail.host is not set, so no e-mail can be sent");
			return;
		}
		try {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(from);
			message.setTo(to);
			message.setSubject(subject);
			message.setText(text);
			mailSender.send(message);
			log.info("E-mail sent: '{}'", subject); // the address is not logged on purpose
		} catch (MailException e) {
			log.error("Could not send the e-mail '{}': {}", subject, e.getClass().getSimpleName());
		}
	}
}
