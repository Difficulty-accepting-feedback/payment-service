package com.grow.payment_service.payment.presentation.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grow.payment_service.payment.application.dto.PaymentDetailResponse;
import com.grow.payment_service.payment.application.service.PaymentQueryService;
import com.grow.payment_service.global.dto.RsData;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/query")
public class PaymentQueryController {

	private final PaymentQueryService paymentQueryService;

	/** 회원의 결제 내역 조회 */
	@GetMapping("/member")
	public ResponseEntity<RsData<List<PaymentDetailResponse>>> getPaymentsByMember(
		@RequestParam Long memberId
	) {
		List<PaymentDetailResponse> list = paymentQueryService.getPaymentsByMemberId(memberId);
		return ResponseEntity.ok(
			new RsData<>("200", "결제 내역 조회 성공", list)
		);
	}
}