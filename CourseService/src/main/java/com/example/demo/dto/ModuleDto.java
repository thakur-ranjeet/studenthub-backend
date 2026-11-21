package com.example.demo.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// One module (chapter) of a course, with the titles of its lessons.
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModuleDto {

	private String title;
	private String summary;
	private String duration; // free text, for example "2 hours"
	private List<String> lessons = new ArrayList<>();

	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getSummary() { return summary; }
	public void setSummary(String summary) { this.summary = summary; }
	public String getDuration() { return duration; }
	public void setDuration(String duration) { this.duration = duration; }
	public List<String> getLessons() { return lessons; }
	public void setLessons(List<String> lessons) { this.lessons = lessons; }
}
