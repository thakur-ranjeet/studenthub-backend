package com.example.demo.model;

public enum OrderStatus {
	CREATED,  // checkout started, not paid (yet)
	PAID,     // payment verified on the server (browser callback or Razorpay webhook)
	FAILED,
	REFUNDED  // the whole order was refunded and the courses were taken back
}
