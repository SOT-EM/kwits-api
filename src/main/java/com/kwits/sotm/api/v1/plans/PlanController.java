package com.kwits.sotm.api.v1.plans;

import java.util.List;
import java.util.UUID;

import com.kwits.sotm.api.common.ApiException;
import com.kwits.sotm.api.plans.PlanService;
import com.kwits.sotm.api.plans.entity.Plan;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The protected end of the first round trip: mobile logs in, then calls this with the
 * Supabase access token it was handed.
 */
@RestController
@RequestMapping("/v1/plans")
public class PlanController {

	private final PlanService planService;

	public PlanController(PlanService planService) {
		this.planService = planService;
	}

	@GetMapping
	public List<Plan> myPlans(@AuthenticationPrincipal Jwt jwt) {
		return planService.findPlansHostedBy(userIdFrom(jwt));
	}

	/**
	 * Supabase puts the user's id in the standard {@code sub} claim. Trusting the token
	 * rather than a client-supplied id is what stops one user reading another's plans.
	 */
	private UUID userIdFrom(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Token has no usable subject claim");
		}
	}
}
