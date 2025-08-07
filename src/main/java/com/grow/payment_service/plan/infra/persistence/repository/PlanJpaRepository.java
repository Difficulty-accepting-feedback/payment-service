package com.grow.payment_service.plan.infra.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.grow.payment_service.plan.infra.persistence.entity.PlanJpaEntity;

public interface PlanJpaRepository extends JpaRepository<PlanJpaEntity, Long> {
}