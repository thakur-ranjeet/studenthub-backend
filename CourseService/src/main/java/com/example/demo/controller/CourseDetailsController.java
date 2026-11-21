package com.example.demo.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.CourseDetailsDto;
import com.example.demo.dto.CourseSummaryDto;
import com.example.demo.repository.CourseRepository;
import com.example.demo.service.CourseDetailsService;

// Reading is open to every signed-in user. Saving is restricted to administrators by the API gateway
// (every POST / PUT / DELETE under /api/v1/courses needs ROLE_ADMIN).
@RestController
@RequestMapping("/api/v1/courses")
public class CourseDetailsController {

	private final CourseDetailsService service;
	private final CourseRepository courses;

	public CourseDetailsController(CourseDetailsService service, CourseRepository courses) {
		this.service = service;
		this.courses = courses;
	}

	// http://localhost:8083/api/v1/courses/details-summary   (tagline, level, module and lesson counts for the course cards)
	@GetMapping("/details-summary")
	public List<CourseSummaryDto> summaries() {
		return service.summaries();
	}

	// http://localhost:8083/api/v1/courses/1/details
	@GetMapping("/{cid}/details")
	public ResponseEntity<?> details(@PathVariable int cid) {
		if (!courses.existsById(cid)) {
			return error(404, "Course not found.");
		}
		return ResponseEntity.ok(service.get(cid));
	}

	@PutMapping("/{cid}/details")
	public ResponseEntity<?> save(@PathVariable int cid, @RequestBody CourseDetailsDto body) {
		if (!courses.existsById(cid)) {
			return error(404, "Course not found.");
		}
		try {
			return ResponseEntity.ok(service.save(cid, body));
		} catch (CourseDetailsService.InvalidDetailsException e) {
			return error(400, e.getMessage());
		}
	}

	// own error body, because this service's global handler answers every exception with a 404
	private static ResponseEntity<Map<String, Object>> error(int status, String message) {
		return ResponseEntity.status(status).body(Map.of("status", status, "message", message));
	}
}
