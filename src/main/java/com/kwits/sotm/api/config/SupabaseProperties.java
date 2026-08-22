package com.kwits.sotm.api.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed binding for the {@code supabase.*} configuration.
 *
 * <p>All three values come from the environment (see {@code .env.example}); none are
 * hardcoded. Startup fails fast with a clear message if any is missing, which is
 * preferable to a confusing 401 on the first login attempt.
 */
@Validated
@ConfigurationProperties(prefix = "supabase")
public record SupabaseProperties(

		/** Base URL of the Supabase instance, e.g. http://127.0.0.1:54321 for local dev. */
		@NotBlank String url,

		/** Publishable/anon key, sent as the {@code apikey} header on Auth REST calls. */
		@NotBlank String anonKey,

		/** Shared secret Supabase signs its access tokens with. See SecurityConfig. */
		@NotBlank String jwtSecret) {
}
