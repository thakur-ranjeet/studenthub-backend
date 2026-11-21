package com.example.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.model.WebhookEvent;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

	boolean existsByEventId(String eventId);
}
