package com.kwits.sotm.api.v1.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** The 6-digit code the user received by email, submitted to finish signup. */
public record OtpVerifyRequest(
		@Email(message = "must be a valid email address") @NotBlank String email,
		@Pattern(regexp = "\\d{6}", message = "must be a 6-digit code") @NotBlank String otp) {
}