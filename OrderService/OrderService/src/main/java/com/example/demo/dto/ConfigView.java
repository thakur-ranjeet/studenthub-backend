package com.example.demo.dto;

// keyId is the PUBLIC Razorpay key id (safe for the browser). The key secret is never sent anywhere.
public record ConfigView(String keyId, String mode) {
}
