package com.kwits.sotm.api.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed binding for the {@code supabase.*} configuration.
 *
 * <p>Both values come from the environment (see {@code .env.example}); none are
 * hardcoded. Startup fails fast with a clear message if any is missing, which is
 * preferable to a confusing 401 on the first login attempt.
 *
 * <p>JWT verification uses the JWKS endpoint ({@code /auth/v1/.well-known/jwks.json})
 * rather than a shared secret, so {@code SUPABASE_JWT_SECRET} is no longer required.
 * See {@code SecurityConfig.jwtDecoder()}.
 */
@Validated
@ConfigurationProperties(prefix = "supabase")
public record SupabaseProperties(

		/** Base URL of the Supabase instance, e.g. http://127.0.0.1:54321 for local dev. */
		@NotBlank String url,

		/** Publishable/anon key, sent as the {@code apikey} header on Auth REST calls. */
		@NotBlank String anonKey) {
}
