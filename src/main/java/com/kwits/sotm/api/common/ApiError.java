package com.kwits.sotm.api.common;

import java.util.Map;

/**
 * The single error shape every failure returns, so the mobile client only has to
 * parse one thing. {@code fieldErrors} is null unless validation failed.
 */
public record ApiError(
		int status,
		String message,
		Map<String, String> fieldErrors) {

	public static ApiError of(int status, String message) {
		return new ApiError(status, message, null);
	}
}
