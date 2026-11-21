package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Request bodies of the AI features. Every text is cut to a safe length on the server.
public final class AiRequests {

	private AiRequests() {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Draft(Integer courseId, String notes) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Rank(String text) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Quiz(Integer courseId, Integer moduleNo, Integer count) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Explain(Integer courseId, String topic) {
	}
}
