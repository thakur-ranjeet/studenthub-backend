package com.example.demo.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

// "At most N per window" for a key (an IP address or an account name). In memory, per gateway instance.
public class ResetRateLimiter {

	private static final int PURGE_ABOVE = 5000; // keeps memory bounded when someone sends many different keys

	private final long windowMs;
	private final LongSupplier clock;
	private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

	public ResetRateLimiter(long windowMs) {
		this(windowMs, System::currentTimeMillis);
	}

	public ResetRateLimiter(long windowMs, LongSupplier clock) {
		this.windowMs = windowMs;
		this.clock = clock;
	}

	public boolean allow(String key, int max) {
		long now = clock.getAsLong();
		if (hits.size() > PURGE_ABOVE) {
			purge(now);
		}
		Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
		synchronized (q) {
			while (!q.isEmpty() && now - q.peekFirst() > windowMs) {
				q.pollFirst();
			}
			if (q.size() >= max) {
				return false;
			}
			q.addLast(now);
			return true;
		}
	}

	private void purge(long now) {
		hits.entrySet().removeIf(e -> {
			synchronized (e.getValue()) {
				Long last = e.getValue().peekLast();
				return last == null || now - last > windowMs;
			}
		});
	}
}
