package com.kwits.sotm.api.v1.auth;

import com.kwits.sotm.api.v1.auth.dto.LoginRequest;
import com.kwits.sotm.api.v1.auth.dto.LoginResponse;
import com.kwits.sotm.api.common.ApiException;
import com.kwits.sotm.api.config.SecurityConfig;
import com.kwits.sotm.api.config.SupabaseProperties;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for the login endpoint. Supabase and Postgres are both mocked out,
 * so these run with no Docker and no credentials -- the parts that genuinely need a
 * live stack are covered by the manual checklist in the README instead.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SupabaseProperties.class)
@TestPropertySource(properties = {
		"supabase.url=http://127.0.0.1:54321",
		"supabase.anon-key=test-anon-key"
})
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@Test
	void loginReturnsTheSupabaseAccessToken() throws Exception {
		BDDMockito.given(authService.login(ArgumentMatchers.any(LoginRequest.class)))
				.willReturn(new LoginResponse("supabase-issued-token", "refresh-token", 3600,
						new LoginResponse.AuthUser("8f1b2c3d-0000-4000-8000-000000000001", "cj@example.com")));

		mockMvc.perform(post("/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"cj@example.com\",\"password\":\"hunter22\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("supabase-issued-token"))
				.andExpect(jsonPath("$.user.email").value("cj@example.com"));
	}

	@Test
	void loginRejectsAMalformedEmailBeforeCallingSupabase() throws Exception {
		mockMvc.perform(post("/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"not-an-email\",\"password\":\"hunter22\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Validation failed"))
				.andExpect(jsonPath("$.fieldErrors.email").exists());

		BDDMockito.then(authService).shouldHaveNoInteractions();
	}

	@Test
	void badCredentialsSurfaceAsUnauthorized() throws Exception {
		BDDMockito.given(authService.login(ArgumentMatchers.any(LoginRequest.class)))
				.willThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

		mockMvc.perform(post("/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"cj@example.com\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}
}
