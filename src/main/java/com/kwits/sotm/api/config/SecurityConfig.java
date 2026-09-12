package com.kwits.sotm.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless security: every route except {@code /v1/auth/**} requires a valid
 * Supabase-issued access token.
 *
 * <p>kwits-api deliberately does not mint its own tokens. The token the mobile app
 * sends is the same one Supabase issued at login, so there is only ever one token
 * system to reason about.
 */
@Configuration
public class SecurityConfig {

	private final SupabaseProperties supabase;

	public SecurityConfig(SupabaseProperties supabase) {
		this.supabase = supabase;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				// No cookies or sessions are used, so there is no CSRF surface to protect.
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/v1/auth/login", "/v1/auth/signup").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
				.build();
	}

	// ---------------------------------------------------------------------------
	// JWT DECODER -- THE ONE PLACE THE SIGNING ALGORITHM IS DECIDED.
	//
	// Uses JWKS-based verification (ES256 asymmetric) because the local Supabase
	// stack is running on the new publishable/secret key system, which signs tokens
	// with an EC key pair rather than a shared HMAC secret.
	//
	// The JWKS endpoint is fetched once and cached by NimbusJwtDecoder; it will
	// automatically refresh when a new key ID (kid) is encountered.
	//
	// If you ever switch back to a legacy Supabase setup that uses a symmetric
	// HS256 secret, replace this method body with:
	//
	//     SecretKeySpec key = new SecretKeySpec(
	//             supabase.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	//     return NimbusJwtDecoder.withSecretKey(key)
	//             .macAlgorithm(MacAlgorithm.HS256)
	//             .build();
	//
	// and restore SUPABASE_JWT_SECRET to the config.
	// ---------------------------------------------------------------------------
	@Bean
	JwtDecoder jwtDecoder() {
		return NimbusJwtDecoder
				.withJwkSetUri(supabase.url() + "/auth/v1/.well-known/jwks.json")
				.build();
	}
}
