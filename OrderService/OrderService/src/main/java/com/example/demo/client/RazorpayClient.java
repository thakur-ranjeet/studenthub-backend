package com.example.demo.client;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.example.demo.exception.ShopException;
import com.fasterxml.jackson.databind.ObjectMapper;

// Talks to Razorpay. Both secrets (API key secret and webhook secret) live only in this class, are read from
// environment variables, and are never logged or sent to the browser.
@Component
public class RazorpayClient {

	private static final Logger log = LoggerFactory.getLogger(RazorpayClient.class);

	public record RefundResult(String id, String status) {
	}

	private final RestTemplate rest;
	private final String keyId;
	private final String keySecret;
	private final String baseUrl;
	private final String webhookSecret;

	public RazorpayClient(RestTemplate rest, @Value("${razorpay.key-id}") String keyId,
			@Value("${razorpay.key-secret}") String keySecret, @Value("${razorpay.base-url}") String baseUrl,
			@Value("${razorpay.webhook-secret:}") String webhookSecret) {
		this.rest = rest;
		this.keyId = keyId;
		this.keySecret = keySecret;
		this.baseUrl = baseUrl;
		this.webhookSecret = webhookSecret;
	}

	public String getKeyId() {
		return keyId;
	}

	public boolean isTestMode() {
		return keyId.startsWith("rzp_test_");
	}

	public boolean isWebhookConfigured() {
		return webhookSecret != null && !webhookSecret.isBlank();
	}

	private HttpHeaders authHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth(keyId, keySecret);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	// ---------- orders ----------

	// Step 1 of every payment: create the order on the server. amount is in paise (INR 499 = 49900).
	public String createOrder(long amountPaise, String currency, String receipt) {
		Map<String, Object> body = Map.of("amount", amountPaise, "currency", currency, "receipt", receipt);
		try {
			Map<?, ?> response = rest.postForObject(baseUrl + "/v1/orders", new HttpEntity<>(body, authHeaders()), Map.class);
			Object id = response == null ? null : response.get("id");
			if (id == null) {
				throw new ShopException(HttpStatus.BAD_GATEWAY, "The payment gateway returned an unexpected answer.");
			}
			return id.toString();
		} catch (HttpStatusCodeException e) {
			log.warn("Razorpay rejected create-order: HTTP {} {}", e.getStatusCode().value(), e.getResponseBodyAsString());
			if (e.getStatusCode().value() == 401) {
				throw new ShopException(HttpStatus.BAD_GATEWAY,
						"Razorpay did not accept the API keys. Check RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET.");
			}
			throw new ShopException(HttpStatus.BAD_GATEWAY, "The payment gateway rejected the request.");
		} catch (RestClientException e) {
			log.warn("Razorpay is not reachable: {}", e.getMessage());
			throw new ShopException(HttpStatus.SERVICE_UNAVAILABLE, "The payment gateway is not reachable right now. Please try again.");
		}
	}

	// ---------- refunds (full refund of one captured payment) ----------

	public RefundResult refund(String paymentId, long amountPaise, long orderId) {
		HttpHeaders headers = authHeaders();
		// the same order always sends the same idempotency key, so a retry can never refund twice
		headers.set("X-Refund-Idempotency",
				UUID.nameUUIDFromBytes(("studenthub-refund-" + orderId).getBytes(StandardCharsets.UTF_8)).toString());
		Map<String, Object> body = Map.of("amount", amountPaise, "speed", "normal", "receipt", "SH-RF-" + orderId);
		try {
			Map<?, ?> r = rest.postForObject(baseUrl + "/v1/payments/" + paymentId + "/refund", new HttpEntity<>(body, headers), Map.class);
			return toRefundResult(r);
		} catch (HttpStatusCodeException e) {
			log.warn("Razorpay rejected refund for order {}: HTTP {} {}", orderId, e.getStatusCode().value(), e.getResponseBodyAsString());
			throw new ShopException(HttpStatus.BAD_REQUEST, "Razorpay could not refund this payment: " + describe(e));
		} catch (RestClientException e) {
			log.warn("Razorpay is not reachable for refund: {}", e.getMessage());
			throw new ShopException(HttpStatus.SERVICE_UNAVAILABLE, "The payment gateway is not reachable right now. Please try again.");
		}
	}

	public RefundResult fetchRefund(String paymentId, String refundId) {
		try {
			Map<?, ?> r = rest.exchange(baseUrl + "/v1/payments/" + paymentId + "/refunds/" + refundId, HttpMethod.GET,
					new HttpEntity<>(authHeaders()), Map.class).getBody();
			return toRefundResult(r);
		} catch (HttpStatusCodeException e) {
			log.warn("Razorpay rejected fetch-refund: HTTP {} {}", e.getStatusCode().value(), e.getResponseBodyAsString());
			throw new ShopException(HttpStatus.BAD_GATEWAY, "Could not read the refund status from Razorpay: " + describe(e));
		} catch (RestClientException e) {
			throw new ShopException(HttpStatus.SERVICE_UNAVAILABLE, "The payment gateway is not reachable right now. Please try again.");
		}
	}

	private static RefundResult toRefundResult(Map<?, ?> r) {
		Object id = r == null ? null : r.get("id");
		Object status = r == null ? null : r.get("status");
		if (id == null || status == null) {
			throw new ShopException(HttpStatus.BAD_GATEWAY, "The payment gateway returned an unexpected answer.");
		}
		return new RefundResult(id.toString(), status.toString());
	}

	// Razorpay's error text (for example "The payment has been fully refunded already"). It contains no secrets.
	private static String describe(HttpStatusCodeException e) {
		try {
			String text = new ObjectMapper().readTree(e.getResponseBodyAsString()).path("error").path("description").asText("");
			if (!text.isBlank()) {
				return text;
			}
		} catch (Exception ignored) {
			// not JSON: fall through
		}
		return "request rejected (HTTP " + e.getStatusCode().value() + ")";
	}

	// ---------- signatures ----------

	// Browser callback: valid only if HMAC-SHA256(order_id + "|" + payment_id, API key secret) equals the signature.
	public boolean verifySignature(String orderId, String paymentId, String signature) {
		if (orderId == null || paymentId == null || signature == null) {
			return false;
		}
		return matches(keySecret, (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8), signature);
	}

	// Webhook: valid only if HMAC-SHA256(raw request body, WEBHOOK secret) equals the X-Razorpay-Signature header.
	// The body must be the raw bytes exactly as received, never re-serialised JSON.
	public boolean verifyWebhookSignature(byte[] rawBody, String signature) {
		if (!isWebhookConfigured() || rawBody == null || signature == null) {
			return false;
		}
		return matches(webhookSecret, rawBody, signature);
	}

	private static boolean matches(String secret, byte[] data, String signature) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			String expected = HexFormat.of().formatHex(mac.doFinal(data));
			// constant-time comparison
			return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.trim().getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			return false;
		}
	}
}
