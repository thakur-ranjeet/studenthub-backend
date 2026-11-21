package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.Instant;

// One wishlist or cart line, joined with the course details.
public record ItemView(int courseId, String cname, String duration, BigDecimal price,
		boolean available, boolean inCart, Instant addedAt) {
}
