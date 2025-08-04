package com.grow.payment_service.plan.infra.persistence.entity;

import com.grow.payment_service.plan.infra.persistence.enums.PlanPeriod;
import com.grow.payment_service.plan.infra.persistence.enums.PlanType;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@Table(name = "plan")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long planId;

	@Column
	@Enumerated(EnumType.STRING)
	private PlanType type;

	@Column
	private Long amount;

	@Column
	@Enumerated(EnumType.STRING)
	private PlanPeriod period;

	@Column(columnDefinition = "TEXT", nullable = false)
	private String benefits;
}