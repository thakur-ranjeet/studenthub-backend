package com.example.demo.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_order")
public class PurchaseOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String username;

	@Column(name = "razorpay_order_id", unique = true, length = 64)
	private String razorpayOrderId;

	@Column(name = "razorpay_payment_id", length = 64)
	private String razorpayPaymentId;

	// money is always stored in paise (integer), never as a floating point number
	@Column(name = "amount_paise", nullable = false)
	private long amountPaise;

	@Column(nullable = false, length = 3)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private OrderStatus status = OrderStatus.CREATED;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "paid_at")
	private Instant paidAt;

	// refund (always for the whole order)
	@Column(name = "refund_id", length = 64)
	private String refundId;

	@Column(name = "refund_status", length = 16)
	private String refundStatus; // pending | processed | failed

	@Column(name = "refunded_at")
	private Instant refundedAt;

	public PurchaseOrder() {
	}

	public PurchaseOrder(String username, long amountPaise, String currency) {
		this.username = username;
		this.amountPaise = amountPaise;
		this.currency = currency;
	}

	public Long getId() { return id; }
	public String getUsername() { return username; }
	public String getRazorpayOrderId() { return razorpayOrderId; }
	public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
	public String getRazorpayPaymentId() { return razorpayPaymentId; }
	public void setRazorpayPaymentId(String razorpayPaymentId) { this.razorpayPaymentId = razorpayPaymentId; }
	public long getAmountPaise() { return amountPaise; }
	public String getCurrency() { return currency; }
	public OrderStatus getStatus() { return status; }
	public void setStatus(OrderStatus status) { this.status = status; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getPaidAt() { return paidAt; }
	public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
	public String getRefundId() { return refundId; }
	public void setRefundId(String refundId) { this.refundId = refundId; }
	public String getRefundStatus() { return refundStatus; }
	public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }
	public Instant getRefundedAt() { return refundedAt; }
	public void setRefundedAt(Instant refundedAt) { this.refundedAt = refundedAt; }
}
