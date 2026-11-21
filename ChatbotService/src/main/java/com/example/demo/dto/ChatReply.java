package com.example.demo.dto;

import java.util.List;

// reply: plain text (the app shows it as text, never as HTML)
// links: pages of the app that the answer is about, built by the server from real course ids
// mode: "ai" or "basic"
public record ChatReply(String reply, List<Link> links, List<String> suggestions, String mode) {

	public record Link(String label, String href) {
	}
}
