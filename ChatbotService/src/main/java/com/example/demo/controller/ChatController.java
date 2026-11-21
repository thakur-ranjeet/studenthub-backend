package com.example.demo.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.ChatReply;
import com.example.demo.dto.ChatRequest;
import com.example.demo.dto.UserContext;
import com.example.demo.service.ChatException;
import com.example.demo.service.ChatService;

// The signed-in user comes from the verified JWT (set by JwtAuthFilter), never from the request body.
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

	private static final Logger log = LoggerFactory.getLogger(ChatController.class);

	private final ChatService chat;

	public ChatController(ChatService chat) {
		this.chat = chat;
	}

	// http://localhost:8083/api/v1/chat/message      { "message": "What is in the Python course?", "history": [ {"role":"user","text":"..."} ] }
	@PostMapping("/message")
	public ChatReply message(@RequestAttribute("username") String username,
			@RequestAttribute(value = "role", required = false) String role,
			@RequestAttribute("token") String token,
			@RequestBody ChatRequest request) {
		return chat.handle(new UserContext(username, role, token), request);
	}

	@ExceptionHandler(ChatException.class)
	ResponseEntity<Map<String, Object>> chatError(ChatException e) {
		return ResponseEntity.status(e.status()).body(Map.of("status", e.status(), "message", e.getMessage()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
		return ResponseEntity.status(400).body(Map.of("status", 400, "message", "Could not read that message."));
	}

	@ExceptionHandler(RuntimeException.class)
	ResponseEntity<Map<String, Object>> unexpected(RuntimeException e) {
		log.error("Chat failed", e);
		return ResponseEntity.status(500).body(Map.of("status", 500, "message", "The assistant had a problem. Please try again."));
	}
}
