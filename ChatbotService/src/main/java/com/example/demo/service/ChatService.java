package com.example.demo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.demo.brain.LlmBrain;
import com.example.demo.brain.LlmUnavailableException;
import com.example.demo.brain.RuleBrain;
import com.example.demo.dto.ChatReply;
import com.example.demo.dto.ChatRequest;
import com.example.demo.dto.UserContext;
import com.example.demo.tools.ChatTools;

@Service
public class ChatService {

	private static final Logger log = LoggerFactory.getLogger(ChatService.class);
	private static final int MAX_HISTORY = 8;
	private static final int MAX_HISTORY_CHARS = 800;

	private final LlmBrain llm;
	private final RuleBrain rules;
	private final RateLimiter limiter;
	private final int maxChars;

	public ChatService(LlmBrain llm, RuleBrain rules, RateLimiter limiter,
			@Value("${chatbot.max-message-chars:500}") int maxChars) {
		this.llm = llm;
		this.rules = rules;
		this.limiter = limiter;
		this.maxChars = maxChars;
	}

	public ChatReply handle(UserContext user, ChatRequest request) {
		String message = request == null || request.message() == null ? "" : request.message().trim();
		if (message.isEmpty()) {
			throw new ChatException(400, "Type a message first.");
		}
		if (message.length() > maxChars) {
			throw new ChatException(400, "Please keep your message under " + maxChars + " characters.");
		}
		if (!limiter.tryAcquire(user.username())) {
			throw new ChatException(429, "You are sending messages too quickly. Please wait a moment.");
		}

		ChatTools.Trace trace = new ChatTools.Trace();
		String reply;
		String mode;
		if (llm.available()) {
			try {
				reply = llm.answer(user, message, cleanHistory(request.history()), trace);
				mode = "ai";
			} catch (LlmUnavailableException e) {
				log.warn("AI mode failed ({}), answering in basic mode", e.getMessage());
				trace = new ChatTools.Trace();
				reply = rules.answer(user, message, trace);
				mode = "basic";
			}
		} else {
			reply = rules.answer(user, message, trace);
			mode = "basic";
		}

		return new ChatReply(reply, links(trace), followUps(trace), mode);
	}

	// The browser sends the earlier turns; keep only the last few, plain user/assistant text, and cut long ones.
	private List<ChatRequest.Turn> cleanHistory(List<ChatRequest.Turn> history) {
		List<ChatRequest.Turn> out = new ArrayList<>();
		if (history == null) {
			return out;
		}
		for (ChatRequest.Turn t : history) {
			if (t == null || t.text() == null || t.text().isBlank()) {
				continue;
			}
			String role = "user".equals(t.role()) ? "user" : "assistant".equals(t.role()) ? "assistant" : null;
			if (role == null || (out.isEmpty() && role.equals("assistant"))) {
				continue; // the conversation must start with the user
			}
			String text = t.text().length() > MAX_HISTORY_CHARS ? t.text().substring(0, MAX_HISTORY_CHARS) : t.text();
			out.add(new ChatRequest.Turn(role, text));
		}
		int from = Math.max(0, out.size() - MAX_HISTORY);
		List<ChatRequest.Turn> tail = new ArrayList<>(out.subList(from, out.size()));
		while (!tail.isEmpty() && tail.get(0).role().equals("assistant")) {
			tail.remove(0);
		}
		return tail;
	}

	// Links come from course ids the lookups really returned, never from text written by the AI.
	private List<ChatReply.Link> links(ChatTools.Trace trace) {
		List<ChatReply.Link> links = new ArrayList<>();
		for (Map.Entry<Integer, String> e : trace.courses().entrySet()) {
			if (links.size() == 3) {
				break;
			}
			links.add(new ChatReply.Link(e.getValue().isBlank() ? "Course #" + e.getKey() : e.getValue(), "#/courses/" + e.getKey()));
		}
		return links;
	}

	private List<String> followUps(ChatTools.Trace trace) {
		return trace.courses().size() == 1
				? List.of("Show the lessons in each module", "Is it good for beginners?")
				: List.of();
	}
}
