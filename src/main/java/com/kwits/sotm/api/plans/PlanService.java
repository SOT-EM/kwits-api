package com.kwits.sotm.api.plans;

import java.util.List;
import java.util.UUID;

import com.kwits.sotm.api.plans.entity.Plan;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {

	private final PlanRepository planRepository;

	public PlanService(PlanRepository planRepository) {
		this.planRepository = planRepository;
	}

	@Transactional(readOnly = true)
	public List<Plan> findPlansHostedBy(UUID userId) {
		return planRepository.findByHostIdOrderByStartDateAsc(userId);
	}
}
