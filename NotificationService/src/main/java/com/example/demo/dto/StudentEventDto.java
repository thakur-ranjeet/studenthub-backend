package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true) // This tells jackson to ignore extra fields like crsId
public class StudentEventDto {
	private int sid;
	private String sname;
	private String smail;
	
	//Getters and Setters
	
	public int getSid() {
		return sid;
	}
	public void setSid(int sid) {
		this.sid = sid;
	}
	public String getSname() {
		return sname;
	}
	public void setSname(String sname) {
		this.sname = sname;
	}
	public String getSmail() {
		return smail;
	}
	public void setSmail(String smail) {
		this.smail = smail;
	}
	
}
