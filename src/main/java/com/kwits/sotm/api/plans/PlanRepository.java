package com.kwits.sotm.api.plans;

import java.util.List;
import java.util.UUID;

import com.kwits.sotm.api.plans.entity.Plan;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

	List<Plan> findByHostIdOrderByStartDateAsc(UUID hostId);
}
