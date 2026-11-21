package com.example.demo.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// The part of the Course service's Course JSON this service needs.
@JsonIgnoreProperties(ignoreUnknown = true)
public class CourseDto {

	private int cid;
	private String cname;
	private String duration;
	private BigDecimal price;

	public int getCid() { return cid; }
	public void setCid(int cid) { this.cid = cid; }
	public String getCname() { return cname; }
	public void setCname(String cname) { this.cname = cname; }
	public String getDuration() { return duration; }
	public void setDuration(String duration) { this.duration = duration; }
	public BigDecimal getPrice() { return price; }
	public void setPrice(BigDecimal price) { this.price = price; }

	public boolean isForSale() {
		return price != null && price.signum() > 0;
	}
}
