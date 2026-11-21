package com.example.demo.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// The long description of a course (introduction, what you will learn, modules ...).
// One row per course, kept apart from Course so the course list stays small and fast.
// The three lists are stored as JSON text.
@Entity
@Table(name = "course_details")
public class CourseDetails {

	@Id
	@Column(name = "cid")
	private int cid;

	@Column(name = "tagline", length = 200)
	private String tagline;

	@Column(name = "description", columnDefinition = "LONGTEXT")
	private String description;

	@Column(name = "course_level", length = 30)
	private String level;

	@Column(name = "course_language", length = 40)
	private String language;

	@Column(name = "instructor", length = 120)
	private String instructor;

	@Column(name = "prerequisites", columnDefinition = "LONGTEXT")
	private String prerequisites;

	@Column(name = "outcomes", columnDefinition = "LONGTEXT")
	private String outcomes; // JSON array of strings

	@Column(name = "audience", columnDefinition = "LONGTEXT")
	private String audience; // JSON array of strings

	@Column(name = "modules", columnDefinition = "LONGTEXT")
	private String modules; // JSON array of modules

	@Column(name = "updated_at")
	private Instant updatedAt = Instant.now();

	public CourseDetails() {
	}

	public CourseDetails(int cid) {
		this.cid = cid;
	}

	public int getCid() { return cid; }
	public void setCid(int cid) { this.cid = cid; }
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
	public String getOutcomes() { return outcomes; }
	public void setOutcomes(String outcomes) { this.outcomes = outcomes; }
	public String getAudience() { return audience; }
	public void setAudience(String audience) { this.audience = audience; }
	public String getModules() { return modules; }
	public void setModules(String modules) { this.modules = modules; }
	public Instant getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
