package com.kwits.sotm.api.v1.auth;

import com.kwits.sotm.api.v1.auth.dto.LoginRequest;
import com.kwits.sotm.api.v1.auth.dto.LoginResponse;
import com.kwits.sotm.api.v1.auth.dto.SignupRequest;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@PostMapping("/signup")
	public LoginResponse signup(@Valid @RequestBody SignupRequest request) {
		return authService.signup(request);
	}
}
