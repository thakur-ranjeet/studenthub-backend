package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Data for the printable receipt page.
public record ReceiptView(String receiptNumber, long orderId, String razorpayOrderId, String paymentId, String username,
		Instant paidAt, BigDecimal total, String currency, String status, String refundStatus, Instant refundedAt,
		boolean testMode, List<OrderItemView> items) {
}
