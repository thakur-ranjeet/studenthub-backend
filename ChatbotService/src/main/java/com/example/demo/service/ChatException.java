package com.example.demo.service;

// An input problem the user can fix. The controller turns it into an HTTP status with this message.
public class ChatException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final int status;

	public ChatException(int status, String message) {
		super(message);
		this.status = status;
	}

	public int status() {
		return status;
	}
}
