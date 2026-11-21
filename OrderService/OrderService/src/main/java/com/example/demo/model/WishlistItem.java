package com.example.demo.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "wishlist_item", uniqueConstraints = @UniqueConstraint(columnNames = { "username", "course_id" }))
public class WishlistItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String username;

	@Column(name = "course_id", nullable = false)
	private int courseId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	public WishlistItem() {
	}

	public WishlistItem(String username, int courseId) {
		this.username = username;
		this.courseId = courseId;
	}

	public Long getId() { return id; }
	public String getUsername() { return username; }
	public int getCourseId() { return courseId; }
	public Instant getCreatedAt() { return createdAt; }
}
