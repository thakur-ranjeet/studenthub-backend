package com.example.demo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.PurchaseService;
import com.example.demo.service.WebhookResult;

// Razorpay calls this URL by itself, so there is no JWT here. The X-Razorpay-Signature check is the lock.
// The body is taken as raw bytes on purpose: the signature is computed over the exact bytes Razorpay sent.
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

	private final PurchaseService purchases;

	public WebhookController(PurchaseService purchases) {
		this.purchases = purchases;
	}

	@PostMapping("/razorpay")
	public ResponseEntity<String> razorpay(@RequestBody byte[] body,
			@RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
			@RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
		WebhookResult result = purchases.handleWebhook(body, signature, eventId);
		return switch (result) {
		case OK, DUPLICATE -> ResponseEntity.ok("ok"); // 2xx tells Razorpay to stop retrying
		case BAD_SIGNATURE, BAD_PAYLOAD -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body("rejected");
		case NOT_CONFIGURED -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("webhook not configured");
		};
	}
}
