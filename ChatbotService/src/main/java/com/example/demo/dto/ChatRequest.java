package com.example.demo.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// What the browser sends: the new message plus the last few turns (the server keeps no chat state).
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatRequest(String message, List<Turn> history) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Turn(String role, String text) {
	}
}
