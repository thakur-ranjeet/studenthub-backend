package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// The three values Razorpay Checkout hands back after a payment.
public record VerifyRequest(
		@JsonProperty("razorpay_order_id") String razorpayOrderId,
		@JsonProperty("razorpay_payment_id") String razorpayPaymentId,
		@JsonProperty("razorpay_signature") String razorpaySignature) {
}
