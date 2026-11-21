package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.AiRequests;
import com.example.demo.dto.UserContext;
import com.example.demo.service.AiService;
import com.fasterxml.jackson.databind.JsonNode;

// The signed-in user comes from the verified JWT (JwtAuthFilter), never from the request body.
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

	private final AiService ai;

	public AiController(AiService ai) {
		this.ai = ai;
	}

	// admin only: which provider/model is in use and the last error from it (never the key)
	@GetMapping("/status")
	public JsonNode status(@RequestAttribute("username") String u, @RequestAttribute(value = "role", required = false) String r,
			@RequestAttribute("token") String t) {
		return ai.status(new UserContext(u, r, t));
	}

	// admin only: { "courseId": 1, "notes": "focus on beginners" }  ->  { draft: {...}, note }
	@PostMapping("/course-draft")
	public JsonNode draft(@RequestAttribute("username") String u, @RequestAttribute(value = "role", required = false) String r,
			@RequestAttribute("token") String t, @RequestBody AiRequests.Draft body) {
		return ai.draft(new UserContext(u, r, t), body);
	}

	// { "text": "I want to become a backend developer" }  ->  { summary, mode, picks: [...] }
	@PostMapping("/recommend")
	public JsonNode recommend(@RequestAttribute("username") String u, @RequestAttribute(value = "role", required = false) String r,
			@RequestAttribute("token") String t, @RequestBody(required = false) AiRequests.Rank body) {
		return ai.recommend(new UserContext(u, r, t), body);
	}

	// { "text": "web development for beginners under 4000" }  ->  { summary, mode, picks: [...] }
	@PostMapping("/search")
	public JsonNode search(@RequestAttribute("username") String u, @RequestAttribute(value = "role", required = false) String r,
			@RequestAttribute("token") String t, @RequestBody AiRequests.Rank body) {
		return ai.search(new UserContext(u, r, t), body);
	}

	// students who own the course (and admins): { "courseId": 1, "moduleNo": 2, "count": 5 }  ->  { questions: [...] }
	@PostMapping("/quiz")
	public JsonNode quiz(@RequestAttribute("username") String u, @RequestAttribute(value = "role", required = false) String r,
			@RequestAttribute("token") String t, @RequestBody AiRequests.Quiz body) {
		return ai.quiz(new UserContext(u, r, t), body);
	}

	// students who own the course (and admins): { "courseId": 1, "topic": "what is a list?" }  ->  { explanation }
	@PostMapping("/explain")
	public JsonNode explain(@RequestAttribute("username") String u, @RequestAttribute(value = "role", required = false) String r,
			@RequestAttribute("token") String t, @RequestBody AiRequests.Explain body) {
		return ai.explain(new UserContext(u, r, t), body);
	}
}
