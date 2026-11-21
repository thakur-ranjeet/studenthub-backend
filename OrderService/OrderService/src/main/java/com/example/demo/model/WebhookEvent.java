package com.example.demo.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Razorpay delivers webhooks "at least once". Remembering each event id makes repeats harmless.
@Entity
@Table(name = "webhook_event")
public class WebhookEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, unique = true, length = 100)
	private String eventId;

	@Column(name = "event_type", nullable = false, length = 60)
	private String eventType;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt = Instant.now();

	public WebhookEvent() {
	}

	public WebhookEvent(String eventId, String eventType) {
		this.eventId = eventId;
		this.eventType = eventType;
	}

	public Long getId() { return id; }
	public String getEventId() { return eventId; }
	public String getEventType() { return eventType; }
	public Instant getReceivedAt() { return receivedAt; }
}
