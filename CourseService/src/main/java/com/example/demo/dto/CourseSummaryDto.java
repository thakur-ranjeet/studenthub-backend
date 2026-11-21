package com.example.demo.dto;

// A few facts per course for the course cards (tagline, level, how many modules and lessons).
public record CourseSummaryDto(int cid, String tagline, String level, int moduleCount, int lessonCount) {
}
