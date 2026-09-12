package com.kwits.sotm.api.v1.auth.dto;

/**
 * Returned after a successful signup. No tokens are present because the account
 * is not yet confirmed; the client must show a code-entry step and call
 * {@code POST /v1/auth/otp/verify} to complete the flow.
 */
public record SignupResponse(
		String message,
		String email) {
}