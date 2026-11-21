package com.example.demo.dto;

import java.time.Instant;

public record EnrollmentView(int courseId, String cname, String duration, Instant enrolledAt, long orderId) {
}
