package com.example.demo.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.demo.dto.CourseDetailsDto;
import com.example.demo.dto.CourseSummaryDto;
import com.example.demo.dto.ModuleDto;
import com.example.demo.model.CourseDetails;
import com.example.demo.repository.CourseDetailsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class CourseDetailsService {

	private static final Logger log = LoggerFactory.getLogger(CourseDetailsService.class);

	// limits (the admin form uses the same numbers)
	private static final int MAX_TAGLINE = 200;
	private static final int MAX_DESCRIPTION = 8000;
	private static final int MAX_PREREQUISITES = 2000;
	private static final int MAX_LANGUAGE = 40;
	private static final int MAX_INSTRUCTOR = 120;
	private static final int MAX_LIST_ITEMS = 30;
	private static final int MAX_LIST_TEXT = 200;
	private static final int MAX_MODULES = 60;
	private static final int MAX_LESSONS = 50;
	private static final int MAX_MODULE_TITLE = 150;
	private static final int MAX_MODULE_SUMMARY = 1000;
	private static final int MAX_MODULE_DURATION = 40;
	private static final int MAX_LESSON_TITLE = 150;
	private static final Set<String> LEVELS = Set.of("Beginner", "Intermediate", "Advanced", "All levels");

	private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
	};
	private static final TypeReference<List<ModuleDto>> MODULES = new TypeReference<>() {
	};

	// thrown for input the administrator can fix; the controller turns it into HTTP 400 with this message
	public static class InvalidDetailsException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		public InvalidDetailsException(String message) {
			super(message);
		}
	}

	private final CourseDetailsRepository repository;
	private final ObjectMapper mapper;

	public CourseDetailsService(CourseDetailsRepository repository, ObjectMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
	}

	// ---------- reading ----------

	// A course without saved details simply returns an empty description (the page then says "coming soon").
	public CourseDetailsDto get(int cid) {
		return repository.findById(cid).map(this::toDto).orElseGet(CourseDetailsDto::new);
	}

	public List<CourseSummaryDto> summaries() {
		return repository.findAll().stream().map(e -> {
			List<ModuleDto> modules = readModules(e.getModules());
			int lessons = modules.stream().mapToInt(m -> m.getLessons() == null ? 0 : m.getLessons().size()).sum();
			return new CourseSummaryDto(e.getCid(), e.getTagline(), e.getLevel(), modules.size(), lessons);
		}).toList();
	}

	// ---------- writing (validate, trim, drop blank entries) ----------

	public CourseDetailsDto save(int cid, CourseDetailsDto in) {
		if (in == null) {
			throw new InvalidDetailsException("Course details are missing.");
		}
		CourseDetails e = repository.findById(cid).orElseGet(() -> new CourseDetails(cid));

		e.setTagline(text(in.getTagline(), MAX_TAGLINE, "Tagline"));
		e.setDescription(text(in.getDescription(), MAX_DESCRIPTION, "Description"));
		String level = text(in.getLevel(), 30, "Level");
		if (level != null && !LEVELS.contains(level)) {
			throw new InvalidDetailsException("Level must be Beginner, Intermediate, Advanced or All levels.");
		}
		e.setLevel(level);
		e.setLanguage(text(in.getLanguage(), MAX_LANGUAGE, "Language"));
		e.setInstructor(text(in.getInstructor(), MAX_INSTRUCTOR, "Instructor"));
		e.setPrerequisites(text(in.getPrerequisites(), MAX_PREREQUISITES, "Requirements"));
		e.setOutcomes(toJson(list(in.getOutcomes(), MAX_LIST_ITEMS, MAX_LIST_TEXT, "What you will learn")));
		e.setAudience(toJson(list(in.getAudience(), MAX_LIST_ITEMS, MAX_LIST_TEXT, "Who this course is for")));

		List<ModuleDto> source = in.getModules() == null ? List.of() : in.getModules();
		if (source.size() > MAX_MODULES) {
			throw new InvalidDetailsException("A course can have at most " + MAX_MODULES + " modules.");
		}
		List<ModuleDto> modules = new ArrayList<>();
		int number = 0;
		for (ModuleDto m : source) {
			number++;
			if (m == null) {
				continue;
			}
			String label = "Module " + number;
			String title = text(m.getTitle(), MAX_MODULE_TITLE, label + " title");
			if (title == null) {
				throw new InvalidDetailsException(label + " needs a title.");
			}
			ModuleDto clean = new ModuleDto();
			clean.setTitle(title);
			clean.setSummary(text(m.getSummary(), MAX_MODULE_SUMMARY, label + " summary"));
			clean.setDuration(text(m.getDuration(), MAX_MODULE_DURATION, label + " duration"));
			clean.setLessons(list(m.getLessons(), MAX_LESSONS, MAX_LESSON_TITLE, label + " lessons"));
			modules.add(clean);
		}
		e.setModules(toJson(modules));
		e.setUpdatedAt(Instant.now());
		return toDto(repository.save(e));
	}

	// ---------- helpers ----------

	private static String text(String value, int max, String field) {
		if (value == null) {
			return null;
		}
		String t = value.trim();
		if (t.isEmpty()) {
			return null;
		}
		if (t.length() > max) {
			throw new InvalidDetailsException(field + " is too long (maximum " + max + " characters).");
		}
		return t;
	}

	private static List<String> list(List<String> values, int maxItems, int maxText, String field) {
		List<String> out = new ArrayList<>();
		if (values == null) {
			return out;
		}
		for (String v : values) {
			String t = text(v, maxText, field);
			if (t != null) {
				out.add(t);
			}
		}
		if (out.size() > maxItems) {
			throw new InvalidDetailsException(field + ": at most " + maxItems + " entries are allowed.");
		}
		return out;
	}

	private CourseDetailsDto toDto(CourseDetails e) {
		CourseDetailsDto d = new CourseDetailsDto();
		d.setTagline(e.getTagline());
		d.setDescription(e.getDescription());
		d.setLevel(e.getLevel());
		d.setLanguage(e.getLanguage());
		d.setInstructor(e.getInstructor());
		d.setPrerequisites(e.getPrerequisites());
		d.setOutcomes(readStrings(e.getOutcomes()));
		d.setAudience(readStrings(e.getAudience()));
		d.setModules(readModules(e.getModules()));
		d.setUpdatedAt(e.getUpdatedAt());
		return d;
	}

	private List<String> readStrings(String json) {
		if (json == null || json.isBlank()) {
			return new ArrayList<>();
		}
		try {
			return mapper.readValue(json, STRINGS);
		} catch (JsonProcessingException ex) {
			log.warn("Could not read a stored list: {}", ex.getOriginalMessage());
			return new ArrayList<>();
		}
	}

	private List<ModuleDto> readModules(String json) {
		if (json == null || json.isBlank()) {
			return new ArrayList<>();
		}
		try {
			List<ModuleDto> modules = mapper.readValue(json, MODULES);
			for (ModuleDto m : modules) {
				if (m.getLessons() == null) {
					m.setLessons(new ArrayList<>());
				}
			}
			return modules;
		} catch (JsonProcessingException ex) {
			log.warn("Could not read stored modules: {}", ex.getOriginalMessage());
			return new ArrayList<>();
		}
	}

	private String toJson(Object value) {
		try {
			return mapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not store the course details", ex);
		}
	}
}
