package com.kwits.sotm.api.plans.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps the {@code travel_plans} table created in
 * {@code supabase/migrations/20260818202046_initial_schema.sql}.
 *
 * <p>Field names match kwits-mobile's {@code TravelPlan} interface, so the serialised
 * JSON needs no renaming on the client.
 */
@Entity
@Table(name = "travel_plans")
public class Plan {

	@Id
	private UUID id;

	@Column(name = "host_id", nullable = false)
	private UUID hostId;

	@Column(nullable = false)
	private String title;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	/** Nullable: estimatedCost is optional on the client. PHP currency amount. */
	@Column(name = "estimated_cost")
	private BigDecimal estimatedCost;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	protected Plan() {
		// required by JPA
	}

	public UUID getId() {
		return id;
	}

	public UUID getHostId() {
		return hostId;
	}

	public String getTitle() {
		return title;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public BigDecimal getEstimatedCost() {
		return estimatedCost;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}
}
