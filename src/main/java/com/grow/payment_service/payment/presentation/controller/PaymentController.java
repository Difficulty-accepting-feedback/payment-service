package com.grow.payment_service.payment.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.grow.payment_service.global.dto.RsData;
import com.grow.payment_service.payment.application.dto.*;
import com.grow.payment_service.payment.application.service.PaymentApplicationService;
import com.grow.payment_service.payment.presentation.dto.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "결제/빌링 관련 API")
public class PaymentController {

	private final PaymentApplicationService paymentService;

	@Operation(summary = "주문 정보 생성", description = "회원/플랜/금액으로 주문을 생성하고 결제 위젯 초기화에 필요한 데이터를 반환합니다.")
	@PostMapping("/create")
	public ResponseEntity<RsData<PaymentInitResponse>> createPayment(
		@Parameter(description = "요청자 회원 ID")
		@RequestHeader("X-Authorization-Id") Long memberId,
		@Parameter(description = "플랜 ID")
		@RequestParam Long planId,
		@Parameter(description = "결제 금액")
		@RequestParam int amount
	) {
		PaymentInitResponse dto = paymentService.initPaymentData(memberId, planId, amount);
		return ResponseEntity.ok(new RsData<>("200", "주문 정보 생성 성공", dto));
	}

	@Operation(summary = "결제 승인", description = "토스 위젯에서 받은 paymentKey로 결제를 승인합니다. 멱등키(Idempotency-Key) 사용을 권장합니다.")
	@PostMapping("/confirm")
	public ResponseEntity<RsData<Long>> confirmPayment(
		@Parameter(description = "요청자 회원 ID")
		@RequestHeader("X-Authorization-Id") Long memberId,
		@Parameter(description = "멱등 키")
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@Valid @RequestBody PaymentConfirmRequest req
	) {
		Long paymentId = paymentService.confirmPayment(
			memberId, req.getPaymentKey(), req.getOrderId(), req.getAmount(), idempotencyKey
		);
		return ResponseEntity.ok(new RsData<>("200", "결제 승인 성공", paymentId));
	}

	@Operation(summary = "결제 취소", description = "승인된 결제를 취소합니다. 부분 취소도 지원합니다.")
	@PostMapping("/cancel")
	public ResponseEntity<RsData<PaymentCancelResponse>> cancelPayment(
		@Parameter(description = "요청자 회원 ID")
		@RequestHeader("X-Authorization-Id") Long memberId,
		@Valid @RequestBody PaymentCancelRequest req
	) {
		PaymentCancelResponse res = paymentService.cancelPayment(
			memberId, req.getOrderId(), req.getCancelAmount(), req.getCancelReason()
		);
		return ResponseEntity.ok(new RsData<>("200", "결제 취소 성공", res));
	}

	@Operation(summary = "자동결제 빌링키 발급", description = "자동결제를 위한 빌링키를 발급합니다.")
	@PostMapping("/billing/issue")
	public ResponseEntity<RsData<String>> issueBillingKey(
		@Parameter(description = "요청자 회원 ID")
		@RequestHeader("X-Authorization-Id") Long memberId,
		@Valid @RequestBody PaymentIssueBillingKeyRequest req
	) {
		PaymentIssueBillingKeyParam param = PaymentIssueBillingKeyParam.builder()
			.orderId(req.getOrderId())
			.authKey(req.getAuthKey())
			.customerKey(req.getCustomerKey())
			.build();

		String billingKey = paymentService.issueBillingKey(memberId, param).getBillingKey();
		return ResponseEntity.ok(new RsData<>("200", "빌링키 발급 성공", billingKey));
	}

	@Operation(summary = "자동결제 승인(빌링키 결제)", description = "발급된 빌링키로 자동결제를 승인합니다. 멱등키(Idempotency-Key) 사용을 권장합니다.")
	@PostMapping("/billing/charge")
	public ResponseEntity<RsData<PaymentConfirmResponse>> chargeWithBillingKey(
		@Parameter(description = "요청자 회원 ID")
		@RequestHeader("X-Authorization-Id") Long memberId,
		@Parameter(description = "멱등 키")
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

		PaymentConfirmResponse res = paymentService.chargeWithBillingKey(memberId, param, idempotencyKey);
		return ResponseEntity.ok(new RsData<>("200", "자동결제 승인 성공", res));
	}

	@Operation(summary = "테스트용 빌링키 준비 상태 전이", description = "테스트 목적: 결제 건을 빌링키 준비 상태로 전이합니다.")
	@PostMapping("/billing/ready")
	public ResponseEntity<RsData<Void>> testBillingReady(@RequestBody PaymentTestBillingReadyRequest req) {
		paymentService.testTransitionToReady(req.getOrderId(), req.getBillingKey());
		return ResponseEntity.ok(new RsData<>("200", "빌링키 준비 상태 전이 성공", null));
	}
}