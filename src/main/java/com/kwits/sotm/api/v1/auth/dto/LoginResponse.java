package com.kwits.sotm.api.v1.auth.dto;

/**
 * What the mobile app receives. {@code accessToken} is Supabase's own JWT, passed
 * straight through -- kwits-api validates this same token on protected routes.
 */
public record LoginResponse(
		String accessToken,
		String refreshToken,
		long expiresIn,
		AuthUser user) {

	public record AuthUser(String id, String email) {
	}
}
