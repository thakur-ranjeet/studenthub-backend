package com.example.demo.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ShopException.class)
	public ResponseEntity<Map<String, Object>> handleShop(ShopException e) {
		return body(e.getStatus(), e.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
		return body(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on the server.");
	}

	private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("timestamp", Instant.now().toString());
		m.put("status", status.value());
		m.put("error", status.getReasonPhrase());
		m.put("message", message);
		return ResponseEntity.status(status).body(m);
	}
}
