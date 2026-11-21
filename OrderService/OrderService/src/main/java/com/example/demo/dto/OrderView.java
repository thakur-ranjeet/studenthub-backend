package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderView(long id, String razorpayOrderId, String paymentId, String username, BigDecimal total,
		String currency, String status, String refundStatus, Instant createdAt, Instant paidAt, Instant refundedAt,
		List<OrderItemView> items) {
}
