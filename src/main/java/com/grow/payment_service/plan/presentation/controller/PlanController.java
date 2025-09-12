package com.grow.payment_service.plan.presentation.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.grow.payment_service.global.dto.RsData;
import com.grow.payment_service.plan.domain.repository.PlanRepository;
import com.grow.payment_service.plan.presentation.dto.PlanResponse;

import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/v1/payment/plans")
@RequiredArgsConstructor
@Tag(name = "Plan", description = "플랜 조회 API")
public class PlanController {

	private final PlanRepository planRepository;

	@Operation(summary = "플랜 목록 조회", description = "구독/일회성 등 모든 플랜 목록을 조회합니다.")
	@GetMapping
	public ResponseEntity<RsData<List<PlanResponse>>> getAllPlans() {
		List<PlanResponse> list = planRepository.findAll().stream()
			.map(p -> new PlanResponse(
				p.getPlanId(), p.getType(), p.getPeriod(), p.getAmount(), p.getBenefits()
			))
			.collect(Collectors.toList());
		return ResponseEntity.ok(new RsData<>("200", "플랜 리스트 조회 성공", list));
	}
}