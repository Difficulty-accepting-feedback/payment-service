package com.grow.payment_service.payment.presentation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grow.payment_service.payment.application.service.PaymentService;
import com.grow.payment_service.payment.infra.paymentprovider.TossInitResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentService paymentService;

	// 결제 생성
	@PostMapping("/create")
	public ResponseEntity<TossInitResponse> create(
		@RequestParam Long memberId,
		@RequestParam Long planId,
		@RequestParam Long orderId,
		@RequestParam int amount
	) {
		TossInitResponse init = paymentService.createPayment(memberId, planId, orderId, amount);
		return ResponseEntity.ok(init);
	}

	// 결제 승인
	@GetMapping("/confirm")
	public ResponseEntity<Long> confirmGet(
		@RequestParam String paymentKey,
		@RequestParam String orderId,
		@RequestParam int amount
	) {
		// orderId로 memberId 꺼내오기
		Long orderIdL = Long.parseLong(orderId);
		Long memberId = paymentService.getMemberIdByOrderId(orderIdL);

		// 내부 승인 로직 호출
		Long paymentId = paymentService
			.confirmPayment(paymentKey, orderId, amount, memberId)
			.getPaymentId();

		return ResponseEntity.ok(paymentId);
	}
}