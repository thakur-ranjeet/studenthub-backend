package com.example.demo.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// What the course page shows (and what an administrator edits).
@JsonIgnoreProperties(ignoreUnknown = true)
public class CourseDetailsDto {

	private String tagline;
	private String description;
	private String level;
	private String language;
	private String instructor;
	private String prerequisites;
	private List<String> outcomes = new ArrayList<>();
	private List<String> audience = new ArrayList<>();
	private List<ModuleDto> modules = new ArrayList<>();

	@JsonProperty(access = JsonProperty.Access.READ_ONLY)
	private Instant updatedAt;

	public String getTagline() { return tagline; }
	public void setTagline(String tagline) { this.tagline = tagline; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public String getLevel() { return level; }
	public void setLevel(String level) { this.level = level; }
	public String getLanguage() { return language; }
	public void setLanguage(String language) { this.language = language; }
	public String getInstructor() { return instructor; }
	public void setInstructor(String instructor) { this.instructor = instructor; }
	public String getPrerequisites() { return prerequisites; }
	public void setPrerequisites(String prerequisites) { this.prerequisites = prerequisites; }
	public List<String> getOutcomes() { return outcomes; }
	public void setOutcomes(List<String> outcomes) { this.outcomes = outcomes; }
	public List<String> getAudience() { return audience; }
	public void setAudience(List<String> audience) { this.audience = audience; }
	public List<ModuleDto> getModules() { return modules; }
	public void setModules(List<ModuleDto> modules) { this.modules = modules; }
	public Instant getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
