package com.grow.payment_service.plan.infra.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.grow.payment_service.plan.domain.model.Plan;
import com.grow.payment_service.plan.domain.repository.PlanRepository;
import com.grow.payment_service.plan.infra.persistence.entity.PlanJpaEntity;
import com.grow.payment_service.plan.infra.persistence.mapper.PlanMapper;
import com.grow.payment_service.plan.infra.persistence.repository.PlanJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PlanRepositoryImpl implements PlanRepository {

	private final PlanJpaRepository jpaRepo;

	@Override
	public Plan save(Plan plan) {
		PlanJpaEntity e = PlanMapper.toEntity(plan);
		PlanJpaEntity saved = jpaRepo.save(e);
		return PlanMapper.toDomain(saved);
	}

	@Override
	public Optional<Plan> findById(Long planId) {
		return jpaRepo.findById(planId)
			.map(PlanMapper::toDomain);
	}

	@Override
	public List<Plan> findAll() {
		return jpaRepo.findAll().stream()
			.map(PlanMapper::toDomain)
			.collect(Collectors.toList());
	}

	@Override
	public void deleteById(Long planId) {
		jpaRepo.deleteById(planId);
	}
}