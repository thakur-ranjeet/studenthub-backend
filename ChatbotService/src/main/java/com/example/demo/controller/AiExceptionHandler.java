package com.example.demo.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.demo.service.ChatException;
import com.example.demo.tools.ToolException;

// Turns problems into { status, message } with a message the app can show as it is.
@RestControllerAdvice(assignableTypes = AiController.class)
public class AiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(AiExceptionHandler.class);

	@ExceptionHandler(ChatException.class)
	ResponseEntity<Map<String, Object>> chat(ChatException e) {
		return body(e.status(), e.getMessage());
	}

	// another StudentHub service was unreachable or refused
	@ExceptionHandler(ToolException.class)
	ResponseEntity<Map<String, Object>> service(ToolException e) {
		return body(502, e.getMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
		return body(400, "Could not read that request.");
	}

	@ExceptionHandler(RuntimeException.class)
	ResponseEntity<Map<String, Object>> unexpected(RuntimeException e) {
		log.error("AI request failed", e);
		return body(500, "The AI feature had a problem. Please try again.");
	}

	private static ResponseEntity<Map<String, Object>> body(int status, String message) {
		return ResponseEntity.status(status).body(Map.of("status", status, "message", message));
	}
}
