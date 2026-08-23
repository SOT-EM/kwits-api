package com.kwits.sotm.api.v1.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
		@Email(message = "must be a valid email address") @NotBlank String email,
		@NotBlank String password) {
}
