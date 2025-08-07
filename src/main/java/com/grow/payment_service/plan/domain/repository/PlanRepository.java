package com.grow.payment_service.plan.domain.repository;

import java.util.List;
import java.util.Optional;

import com.grow.payment_service.plan.domain.model.Plan;

public interface PlanRepository {
	Plan save(Plan plan);
	Optional<Plan> findById(Long planId);
	List<Plan> findAll();
	void deleteById(Long planId);
}