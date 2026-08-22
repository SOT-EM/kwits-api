package com.kwits.sotm.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code name} is optional and forwarded to Supabase as user metadata, where the
 * {@code handle_new_user} trigger copies it into {@code profiles.name}.
 */
public record SignupRequest(
		@Email(message = "must be a valid email address") @NotBlank String email,
		@NotBlank @Size(min = 6, message = "must be at least 6 characters") String password,
		String name) {
}
