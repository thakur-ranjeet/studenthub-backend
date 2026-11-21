package com.example.demo.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// One row per reset link that was e-mailed. The link itself is never stored, only its SHA-256 hash.
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Instant expiresAt;

	private Instant usedAt;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	public PasswordResetToken() {
	}

	public PasswordResetToken(String tokenHash, Long userId, Instant expiresAt) {
		this.tokenHash = tokenHash;
		this.userId = userId;
		this.expiresAt = expiresAt;
	}

	public Long getId() { return id; }
	public String getTokenHash() { return tokenHash; }
	public Long getUserId() { return userId; }
	public Instant getExpiresAt() { return expiresAt; }
	public Instant getUsedAt() { return usedAt; }
	public Instant getCreatedAt() { return createdAt; }
}
