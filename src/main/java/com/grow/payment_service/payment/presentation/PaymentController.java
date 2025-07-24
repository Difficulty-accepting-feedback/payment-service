package com.grow.payment_service.payment.presentation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.grow.payment_service.payment.application.dto.PaymentAutoChargeParam;
import com.grow.payment_service.payment.application.dto.PaymentCancelResponse;
import com.grow.payment_service.payment.application.dto.PaymentConfirmResponse;
import com.grow.payment_service.payment.application.dto.PaymentInitResponse;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyParam;
import com.grow.payment_service.payment.application.dto.PaymentIssueBillingKeyResponse;
import com.grow.payment_service.payment.application.service.PaymentApplicationService;
import com.grow.payment_service.payment.presentation.dto.PaymentAutoChargeRequest;
import com.grow.payment_service.payment.presentation.dto.PaymentCancelRequest;
import com.grow.payment_service.payment.presentation.dto.PaymentConfirmRequest;
import com.grow.payment_service.payment.presentation.dto.PaymentIssueBillingKeyRequest;

import jakarta.validation.Valid;
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

	/** 자동결제 빌링키 발급 */
	@PostMapping("/billing/issue")
	public ResponseEntity<PaymentIssueBillingKeyResponse> issueBillingKey(
		@Valid @RequestBody PaymentIssueBillingKeyRequest req
	) {
		// Presentation → Application DTO 변환
		var param = PaymentIssueBillingKeyParam.builder()
			.orderId(req.getOrderId())
			.authKey(req.getAuthKey())
			.customerKey(req.getCustomerKey())
			.build();

		return ResponseEntity.ok(
			paymentService.issueBillingKey(param)
		);
	}

	/** 자동결제 승인(빌링키 결제) */
	@PostMapping("/billing/charge")
	public ResponseEntity<PaymentConfirmResponse> chargeWithBillingKey(
		@Valid @RequestBody PaymentAutoChargeRequest req
	) {
		// Presentation → Application DTO 변환
		var param = PaymentAutoChargeParam.builder()
			.billingKey(req.getBillingKey())
			.customerKey(req.getCustomerKey())
			.amount(req.getAmount())
			.orderId(req.getOrderId())
			.orderName(req.getOrderName())
			.customerEmail(req.getCustomerEmail())
			.customerName(req.getCustomerName())
			.taxFreeAmount(req.getTaxFreeAmount())
			.taxExemptionAmount(req.getTaxExemptionAmount())
			.build();

		return ResponseEntity.ok(
			paymentService.chargeWithBillingKey(param)
		);
	}
}