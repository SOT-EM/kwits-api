package com.kwits.sotm.api.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless security: every route except {@code /auth/**} requires a valid
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
						.requestMatchers("/auth/login", "/auth/signup").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
				.build();
	}

	// ---------------------------------------------------------------------------
	// JWT DECODER -- THE ONE PLACE THE SIGNING ALGORITHM IS DECIDED.
	//
	// This is built for HS256 with a shared secret, which is what local Supabase
	// uses: supabase/config.toml leaves `signing_keys_path` commented out, so the
	// stack falls back to the legacy symmetric secret that `supabase status` prints.
	//
	// A hosted Supabase project on the newer publishable/secret key system may sign
	// asymmetrically instead. If so, replace this method body with:
	//
	//     return NimbusJwtDecoder
	//             .withJwkSetUri(supabase.url() + "/auth/v1/.well-known/jwks.json")
	//             .build();
	//
	// and drop SUPABASE_JWT_SECRET from the config. Nothing else in the codebase
	// needs to change -- that is the point of isolating it here.
	// ---------------------------------------------------------------------------
	@Bean
	JwtDecoder jwtDecoder() {
		SecretKeySpec key = new SecretKeySpec(
				supabase.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		return NimbusJwtDecoder.withSecretKey(key)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
	}
}
