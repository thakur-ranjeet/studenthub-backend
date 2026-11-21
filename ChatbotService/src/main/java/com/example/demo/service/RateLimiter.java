package com.example.demo.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Each message can cost an AI call, so one user may only send a limited number per minute.
// In memory and per instance, which is enough for one chatbot service; use Redis if you ever run several.
@Component
public class RateLimiter {

	private static final long WINDOW_MS = 60_000;

	private final int limit;
	private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

	public RateLimiter(@Value("${chatbot.rate-limit-per-minute:20}") int limit) {
		this.limit = limit;
	}

	public boolean tryAcquire(String user) {
		return tryAcquire(user, limit);
	}

	// own limit per kind of request (AI features are more expensive than chat messages)
	public boolean tryAcquire(String user, int max) {
		long now = System.currentTimeMillis();
		Deque<Long> q = hits.computeIfAbsent(user, k -> new ArrayDeque<>());
		synchronized (q) {
			while (!q.isEmpty() && now - q.peekFirst() > WINDOW_MS) {
				q.pollFirst();
			}
			if (q.size() >= max) {
				return false;
			}
			q.addLast(now);
			return true;
		}
	}
}
