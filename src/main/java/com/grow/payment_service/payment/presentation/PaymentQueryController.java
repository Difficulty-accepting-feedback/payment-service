package com.grow.payment_service.payment.presentation;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grow.payment_service.payment.application.dto.PaymentDetailResponse;
import com.grow.payment_service.payment.application.service.PaymentQueryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/query")
public class PaymentQueryController {

	private final PaymentQueryService paymentQueryService;

	/** 결제 내역 조회 */
	@GetMapping("/history")
	public ResponseEntity<PaymentDetailResponse> getPaymentDetail(
		@RequestParam Long paymentId
	) {
		PaymentDetailResponse response = paymentQueryService.getPayment(paymentId);
		return ResponseEntity.ok(response);
	}

	/** 회원의 결제 내역 조회 */
	@GetMapping("/member")
	public ResponseEntity<List<PaymentDetailResponse>> getPaymentsByMember(
		@RequestParam Long memberId
	) {
		return ResponseEntity.ok(
			paymentQueryService.getPaymentsByMemberId(memberId)
		);
	}
}