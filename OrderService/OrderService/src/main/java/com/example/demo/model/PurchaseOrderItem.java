package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// A snapshot of one course at the moment of purchase (name and price are copied, so later edits do not change old orders).
@Entity
@Table(name = "purchase_order_item")
public class PurchaseOrderItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_id", nullable = false)
	private Long orderId;

	@Column(name = "course_id", nullable = false)
	private int courseId;

	@Column(name = "course_name", nullable = false, length = 200)
	private String courseName;

	@Column(name = "price_paise", nullable = false)
	private long pricePaise;

	public PurchaseOrderItem() {
	}

	public PurchaseOrderItem(Long orderId, int courseId, String courseName, long pricePaise) {
		this.orderId = orderId;
		this.courseId = courseId;
		this.courseName = courseName;
		this.pricePaise = pricePaise;
	}

	public Long getId() { return id; }
	public Long getOrderId() { return orderId; }
	public int getCourseId() { return courseId; }
	public String getCourseName() { return courseName; }
	public long getPricePaise() { return pricePaise; }
}
