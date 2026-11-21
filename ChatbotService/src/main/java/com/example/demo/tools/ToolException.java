package com.example.demo.tools;

// A problem the assistant can explain to the user ("the order service is not reachable").
// The message is written for end users: it never contains internal URLs or stack traces.
public class ToolException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ToolException(String message) {
		super(message);
	}
}
