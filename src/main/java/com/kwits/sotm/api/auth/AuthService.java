package com.kwits.sotm.api.auth;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kwits.sotm.api.auth.dto.LoginRequest;
import com.kwits.sotm.api.auth.dto.LoginResponse;
import com.kwits.sotm.api.auth.dto.SignupRequest;
import com.kwits.sotm.api.common.ApiException;
import com.kwits.sotm.api.config.SupabaseProperties;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Proxies authentication to Supabase's Auth REST API.
 *
 * <p>Supabase remains the identity provider; kwits-api is only a gateway in front of
 * it, so the token returned here is Supabase's own and is what protected routes
 * validate. Adding a second, separately-signed token would mean two key rotations,
 * two expiry policies and two revocation stories for no gain.
 */
@Service
public class AuthService {

	private final RestClient client;

	public AuthService(RestClient.Builder builder, SupabaseProperties supabase) {
		this.client = builder
				.baseUrl(supabase.url())
				.defaultHeader("apikey", supabase.anonKey())
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.build();
	}

	public LoginResponse login(LoginRequest request) {
		SupabaseSession session = client.post()
				.uri(uriBuilder -> uriBuilder.path("/auth/v1/token")
						.queryParam("grant_type", "password")
						.build())
				.body(Map.of("email", request.email(), "password", request.password()))
				.retrieve()
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
				})
				.body(SupabaseSession.class);

		return toLoginResponse(session);
	}

	public LoginResponse signup(SignupRequest request) {
		Map<String, Object> body = new HashMap<>();
		body.put("email", request.email());
		body.put("password", request.password());
		if (request.name() != null && !request.name().isBlank()) {
			// Lands in auth.users.raw_user_meta_data, which the handle_new_user
			// trigger copies into profiles.name.
			body.put("data", Map.of("name", request.name()));
		}

		SupabaseSession session = client.post()
				.uri("/auth/v1/signup")
				.body(body)
				.retrieve()
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					throw new ApiException(HttpStatus.BAD_REQUEST, "Could not create that account");
				})
				.body(SupabaseSession.class);

		return toLoginResponse(session);
	}

	private LoginResponse toLoginResponse(SupabaseSession session) {
		if (session == null) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "No response from the auth provider");
		}
		if (session.accessToken() == null) {
			// Signup with email confirmation enabled returns a user but no session.
			throw new ApiException(HttpStatus.ACCEPTED,
					"Account created. Confirm your email address before signing in.");
		}
		SupabaseSession.SupabaseUser user = session.user();
		return new LoginResponse(
				session.accessToken(),
				session.refreshToken(),
				session.expiresIn(),
				user == null ? null : new LoginResponse.AuthUser(user.id(), user.email()));
	}

	/** Supabase's wire format. Kept private so its snake_case shape stops here. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	private record SupabaseSession(
			@JsonProperty("access_token") String accessToken,
			@JsonProperty("refresh_token") String refreshToken,
			@JsonProperty("expires_in") long expiresIn,
			SupabaseUser user) {

		@JsonIgnoreProperties(ignoreUnknown = true)
		private record SupabaseUser(String id, String email) {
		}
	}
}
