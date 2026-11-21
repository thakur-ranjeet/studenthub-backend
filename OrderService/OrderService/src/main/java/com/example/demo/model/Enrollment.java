package com.example.demo.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// "This user owns this course". Created only after a payment has been verified.
@Entity
@Table(name = "enrollment", uniqueConstraints = @UniqueConstraint(columnNames = { "username", "course_id" }))
public class Enrollment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String username;

	@Column(name = "course_id", nullable = false)
	private int courseId;

	@Column(name = "course_name", nullable = false, length = 200)
	private String courseName;

	@Column(name = "order_id", nullable = false)
	private Long orderId;

	@Column(name = "enrolled_at", nullable = false)
	private Instant enrolledAt = Instant.now();

	public Enrollment() {
	}

	public Enrollment(String username, int courseId, String courseName, Long orderId) {
		this.username = username;
		this.courseId = courseId;
		this.courseName = courseName;
		this.orderId = orderId;
	}

	public Long getId() { return id; }
	public String getUsername() { return username; }
	public int getCourseId() { return courseId; }
	public String getCourseName() { return courseName; }
	public Long getOrderId() { return orderId; }
	public Instant getEnrolledAt() { return enrolledAt; }
}
