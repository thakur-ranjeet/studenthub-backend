package com.example.demo.brain;

// The AI provider could not be used (no key, network, rate limit...). The chat then falls back to basic mode.
public class LlmUnavailableException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public LlmUnavailableException(String message) {
		super(message);
	}
}
