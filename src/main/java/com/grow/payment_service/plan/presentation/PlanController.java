package com.grow.payment_service.plan.presentation;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grow.payment_service.global.dto.RsData;
import com.grow.payment_service.plan.domain.repository.PlanRepository;
import com.grow.payment_service.plan.presentation.dto.PlanResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

	private final PlanRepository planRepository;

	/** 모든 플랜 조회 */
	@GetMapping
	public ResponseEntity<RsData<List<PlanResponse>>> getAllPlans() {
		List<PlanResponse> list = planRepository.findAll().stream()
			.map(p -> new PlanResponse(
				p.getPlanId(),
				p.getType(),
				p.getPeriod(),
				p.getAmount(),
				p.getBenefits()
			))
			.collect(Collectors.toList());
		return ResponseEntity.ok(
			new RsData<>("200", "플랜 리스트 조회 성공", list)
		);
	}
}