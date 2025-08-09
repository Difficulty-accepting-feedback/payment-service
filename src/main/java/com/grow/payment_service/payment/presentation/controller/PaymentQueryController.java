package com.grow.payment_service.payment.presentation.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.grow.payment_service.payment.application.dto.PaymentDetailResponse;
import com.grow.payment_service.payment.application.service.PaymentQueryService;
import com.grow.payment_service.global.dto.RsData;

import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/query")
@Tag(name = "Payment Query", description = "결제 조회 API")
public class PaymentQueryController {

	private final PaymentQueryService paymentQueryService;

	@Operation(summary = "회원 결제 내역 조회", description = "특정 회원의 결제 내역을 조회합니다.")
	@GetMapping("/member")
	public ResponseEntity<RsData<List<PaymentDetailResponse>>> getPaymentsByMember(
		@Parameter(description = "회원 ID")
		@RequestHeader Long memberId
	) {
		List<PaymentDetailResponse> list = paymentQueryService.getPaymentsByMemberId(memberId);
		return ResponseEntity.ok(new RsData<>("200", "결제 내역 조회 성공", list));
	}
}