package com.grow.payment_service.payment.presentation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentInitResponse;
import com.grow.payment_service.payment.application.service.PaymentApplicationService;
import com.grow.payment_service.payment.presentation.dto.PaymentCancelRequest;
import com.grow.payment_service.payment.presentation.dto.PaymentConfirmRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentApplicationService paymentService;

	/** 주문 정보 생성 */
	@PostMapping("/create")
	public ResponseEntity<PaymentInitResponse> createPayment(
		@RequestParam Long memberId,
		@RequestParam Long planId,
		@RequestParam Long orderId,
		@RequestParam int amount
	) {
		PaymentInitResponse dto = paymentService.initPaymentData(memberId, planId, orderId, amount);
		return ResponseEntity.ok(dto);
	}

	/** 결제 승인 */
	@PostMapping("/confirm")
	public ResponseEntity<Long> confirmPayment(@RequestBody PaymentConfirmRequest req) {
		Long paymentId = paymentService.confirmPayment(req.getPaymentKey(), req.getOrderId(), req.getAmount());
		return ResponseEntity.ok(paymentId);
	}


	/** 결제 취소 */
	@PostMapping("/cancel")
	public ResponseEntity<PaymentCancelResponse> cancelPayment(
		@RequestBody PaymentCancelRequest req
	) {
		PaymentCancelResponse res = paymentService.cancelPayment(
			req.getPaymentKey(),
			req.getOrderId(),
			req.getCancelAmount(),
			req.getCancelReason()
		);
		return ResponseEntity.ok(res);
	}
}