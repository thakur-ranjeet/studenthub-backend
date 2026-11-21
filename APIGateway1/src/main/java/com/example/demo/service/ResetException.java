package com.example.demo.service;

// A reset problem the user can understand (bad or expired link, weak password).
public class ResetException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ResetException(String message) {
		super(message);
	}
}
