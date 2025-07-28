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
import com.grow.payment_service.payment.application.service.PaymentApplicationService;
import com.grow.payment_service.payment.presentation.dto.PaymentAutoChargeRequest;
import com.grow.payment_service.payment.presentation.dto.PaymentCancelRequest;
import com.grow.payment_service.payment.presentation.dto.PaymentConfirmRequest;
import com.grow.payment_service.payment.presentation.dto.PaymentIssueBillingKeyRequest;
import com.grow.payment_service.payment.presentation.dto.PaymentTestBillingReadyRequest;
import com.grow.payment_service.payment.saga.PaymentCompensationSaga;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

	private final PaymentApplicationService paymentService;
	private final PaymentCompensationSaga paymentCompensationSaga;

	/** 주문 정보 생성 */
	@PostMapping("/create")
	public ResponseEntity<PaymentInitResponse> createPayment(
		@RequestParam Long memberId,
		@RequestParam Long planId,
		@RequestParam int amount
	) {
		PaymentInitResponse dto = paymentService.initPaymentData(memberId, planId, amount);
		return ResponseEntity.ok(dto);
	}

	/** 결제 승인 */
	@PostMapping("/confirm")
	public ResponseEntity<Long> confirmPayment(@RequestBody PaymentConfirmRequest req) {
		Long paymentId = paymentCompensationSaga.confirmWithCompensation(
			req.getPaymentKey(), req.getOrderId(), req.getAmount()
		);
		return ResponseEntity.ok(paymentId);
	}

	/** 결제 취소 */
	@PostMapping("/cancel")
	public ResponseEntity<PaymentCancelResponse> cancelPayment(
		@RequestBody PaymentCancelRequest req
	) {
		// paymentService.cancelPayment 대신 Saga 사용
		paymentCompensationSaga.cancelWithCompensation(
			req.getPaymentKey(),
			req.getOrderId(),
			req.getCancelAmount(),
			req.getCancelReason()
		);
		// Saga 안에서 성공/예외 처리되므로, 여기선 간단히 OK만
		return ResponseEntity.ok().build();
	}

	/** 자동결제 빌링키 발급 */
	@PostMapping("/billing/issue")
	public ResponseEntity<String> issueBillingKey(
		@Valid @RequestBody PaymentIssueBillingKeyRequest req
	) {
		PaymentIssueBillingKeyParam param = PaymentIssueBillingKeyParam.builder()
			.orderId(req.getOrderId())
			.authKey(req.getAuthKey())
			.customerKey(req.getCustomerKey())
			.build();

		// paymentService.issueBillingKey 대신 Saga 사용
		String billingKey = paymentCompensationSaga.issueKeyWithCompensation(param);
		return ResponseEntity.ok(billingKey);
	}

	/** 자동결제 승인(빌링키 결제) */
	@PostMapping("/billing/charge")
	public ResponseEntity<PaymentConfirmResponse> chargeWithBillingKey(
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

		// paymentService.chargeWithBillingKey 대신 Saga 사용
		PaymentConfirmResponse res = paymentCompensationSaga.autoChargeWithCompensation(param);
		return ResponseEntity.ok(res);
	}

	/** 테스트용 빌링키 발급 상태 전이 */
	@PostMapping("/billing/ready")
	public ResponseEntity<Void> testBillingReady(@RequestBody PaymentTestBillingReadyRequest req) {
		paymentService.testTransitionToReady(
			req.getOrderId(),
			req.getBillingKey()
		);
		return ResponseEntity.ok().build();
	}
}