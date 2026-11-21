package com.example.demo.controller;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.MailService;

@RestController
@RequestMapping("/api/v1/auth")
public class HelpController {

	public record HelpRequest(String name, String email, String subject, String message) {}

	private final MailService mail;
	private final ExecutorService executor;

	public HelpController(MailService mail, @Qualifier("resetExecutor") ExecutorService executor) {
		this.mail = mail;
		this.executor = executor;
	}

	@PostMapping("/help")
	public ResponseEntity<Map<String, String>> submitQuery(@RequestBody HelpRequest req) {
		if (req == null || req.email() == null || req.email().isBlank()
				|| req.subject() == null || req.subject().isBlank()
				|| req.message() == null || req.message().isBlank()) {
			return ResponseEntity.badRequest().body(Map.of("error", "Please fill in your email, subject, and query message."));
		}
		String senderName = (req.name() == null || req.name().isBlank()) ? "Student" : req.name().trim();
		String email = req.email().trim();
		String subject = req.subject().trim();
		String msg = req.message().trim();

		executor.execute(() -> mail.sendSupportQuery(senderName, email, subject, msg));
		return ResponseEntity.ok(Map.of(
				"message", "Your query has been sent to our support team. We will respond to you via email within 24 hours."
		));
	}
}