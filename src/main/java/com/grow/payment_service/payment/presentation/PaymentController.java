package com.grow.payment_service.payment.presentation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.grow.payment_service.payment.application.dto.*;
import com.grow.payment_service.payment.application.service.PaymentApplicationService;
import com.grow.payment_service.global.dto.RsData;
import com.grow.payment_service.payment.presentation.dto.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentApplicationService paymentService;

	/** 주문 정보 생성 */
	@PostMapping("/create")
	public ResponseEntity<RsData<PaymentInitResponse>> createPayment(
		@RequestParam Long memberId,
		@RequestParam Long planId,
		@RequestParam int amount
	) {
		PaymentInitResponse dto = paymentService.initPaymentData(memberId, planId, amount);
		return ResponseEntity.ok(
			new RsData<>("200", "주문 정보 생성 성공", dto)
		);
	}

	/** 결제 승인 */
	@PostMapping("/confirm")
	public ResponseEntity<RsData<Long>> confirmPayment(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody @Valid PaymentConfirmRequest req
	) {
		Long paymentId = paymentService.confirmPayment(
			req.getPaymentKey(), req.getOrderId(), req.getAmount(), idempotencyKey
		);
		return ResponseEntity.ok(
			new RsData<>("200", "결제 승인 성공", paymentId)
		);
	}

	/** 결제 취소 */
	@PostMapping("/cancel")
	public ResponseEntity<RsData<PaymentCancelResponse>> cancelPayment(
		@RequestBody @Valid PaymentCancelRequest req
	) {
		PaymentCancelResponse res = paymentService.cancelPayment(
			req.getPaymentKey(),
			req.getOrderId(),
			req.getCancelAmount(),
			req.getCancelReason()
		);
		return ResponseEntity.ok(
			new RsData<>("200", "결제 취소 성공", res)
		);
	}

	/** 자동결제 빌링키 발급 */
	@PostMapping("/billing/issue")
	public ResponseEntity<RsData<String>> issueBillingKey(
		@Valid @RequestBody PaymentIssueBillingKeyRequest req
	) {
		PaymentIssueBillingKeyParam param = PaymentIssueBillingKeyParam.builder()
			.orderId(req.getOrderId())
			.authKey(req.getAuthKey())
			.customerKey(req.getCustomerKey())
			.build();

		String billingKey = paymentService.issueBillingKey(param)
			.getBillingKey();

		return ResponseEntity.ok(
			new RsData<>("200", "빌링키 발급 성공", billingKey)
		);
	}

	/** 자동결제 승인(빌링키 결제) */
	@PostMapping("/billing/charge")
	public ResponseEntity<RsData<PaymentConfirmResponse>> chargeWithBillingKey(
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@Valid @RequestBody PaymentAutoChargeRequest req
	) {
		PaymentAutoChargeParam param = PaymentAutoChargeParam.builder()
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

		PaymentConfirmResponse res = paymentService.chargeWithBillingKey(param, idempotencyKey);

		return ResponseEntity.ok(
			new RsData<>("200", "자동결제 승인 성공", res)
		);
	}

	/** 테스트용 빌링키 발급 상태 전이 */
	@PostMapping("/billing/ready")
	public ResponseEntity<RsData<Void>> testBillingReady(
		@RequestBody PaymentTestBillingReadyRequest req
	) {
		paymentService.testTransitionToReady(req.getOrderId(), req.getBillingKey());
		return ResponseEntity.ok(
			new RsData<>("200", "빌링키 준비 상태 전이 성공", null)
		);
	}
}