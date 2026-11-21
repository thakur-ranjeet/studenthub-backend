package com.example.demo.model;

import java.io.Serializable;
import java.math.BigDecimal;

import jakarta.persistence.*;

import jakarta.persistence.Entity;
@Entity
public class Course implements Serializable
{
	private static final long serialVersionUID = 1L; //adding this line for safe version tracking
	
	@Id
	private int cid;
	private String cname;
	private String duration;

	// Price in Indian rupees. null (or 0) means the course is not for sale yet.
	@Column(precision = 10, scale = 2)
	private BigDecimal price;
	public int getCid() {
		return cid;
	}
	public void setCid(int cid) {
		this.cid = cid;
	}
	public String getCname() {
		return cname;
	}
	public void setCname(String cname) {
		this.cname = cname;
	}
	public String getduration() {
		return duration;
	}
	public void setDuration(String duration) {
		this.duration = duration;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}

}
