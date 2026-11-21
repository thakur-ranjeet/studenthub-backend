package com.example.demo.dto;

// Everything the browser needs to open the Razorpay payment window. amount is in paise.
public record CheckoutResponse(long orderId, String razorpayOrderId, long amount, String currency, String keyId, int itemCount) {
}
