package com.kwits.sotm.api.common;

import org.springframework.http.HttpStatusCode;

/** An error that should surface to the client with a specific status and message. */
public class ApiException extends RuntimeException {

	private final HttpStatusCode status;

	public ApiException(HttpStatusCode status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatusCode status() {
		return status;
	}
}
