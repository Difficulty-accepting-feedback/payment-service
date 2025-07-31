package com.grow.payment_service.payment.saga;

import org.springframework.stereotype.Service;

import com.grow.payment_service.payment.application.dto.*;
import com.grow.payment_service.payment.application.service.PaymentPersistenceService;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.service.PaymentGatewayPort;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingAuthResponse;
import com.grow.payment_service.payment.infra.paymentprovider.dto.TossBillingChargeResponse;
import com.grow.payment_service.payment.infra.redis.RedisIdempotencyAdapter;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentSagaOrchestrator {

	private final PaymentGatewayPort gatewayPort;
	private final RetryablePersistenceService retryableService;
	private final RedisIdempotencyAdapter idempotencyAdapter;
	private final PaymentPersistenceService persistenceService;

	/**
	 * 1) 토스 결제 승인 API 호출
	 * 2) DB 저장(재시도 포함) → 실패 시 보상(자동 취소)
	 */
	public Long confirmWithCompensation(String paymentKey, String orderId, int amount, String idempotencyKey) {
		// 이미 처리된 idempotencyKey -> 기존 결제ID 리턴
		if (!idempotencyAdapter.reserve(idempotencyKey)) {
			return persistenceService.findByOrderId(orderId).getPaymentId();
		}
		// 외부 결제 승인
		gatewayPort.confirmPayment(paymentKey, orderId, amount);
		// DB 저장 및 실패 시 보상 로직 실행
		return retryableService.saveConfirmation(paymentKey, orderId, amount);
	}

	/**
	 * 1) DB 저장(재시도 포함) - 취소 요청
	 * 2) 사용자 요청 결제 취소 API 호출
	 * 3) DB 저장(재시도 포함) - 취소 완료
	 */
	public PaymentCancelResponse cancelWithCompensation(
		String paymentKey, String orderId, int amount, CancelReason reason
	) {
		// DB: 취소 요청 저장(리트라이 + 보상)
		retryableService.saveCancelRequest(paymentKey, orderId, amount, reason);
		// 외부 결제 취소
		gatewayPort.cancelPayment(paymentKey, reason.name(), amount, "사용자 요청 취소");
		// DB: 취소 완료 저장(리트라이 + 보상)
		return retryableService.saveCancelComplete(orderId);
	}


	/**
	 * 1) 토스 빌링키 발급 API 호출
	 * 2) DB에 빌링키 저장(재시도 포함)
	 */
	public PaymentIssueBillingKeyResponse issueKeyWithCompensation(PaymentIssueBillingKeyParam param) {
		// 외부 빌링키 발급
		TossBillingAuthResponse toss = gatewayPort.issueBillingKey(param.getAuthKey(), param.getCustomerKey());
		// DB에 발급된 키 저장
		return retryableService.saveBillingKey(param.getOrderId(), toss.getBillingKey());
	}

	/**
	 * 1) 토스 빌링키 자동결제 API 호출
	 * 2) DB 승인 결과 저장(재시도 포함) → 실패 시 보상(자동 취소)
	 */
	public PaymentConfirmResponse autoChargeWithCompensation(PaymentAutoChargeParam param, String idempotencyKey) {
		// 이미 자동결제 처리된 idempotencyKey -> 기존 상태 리턴
		if (!idempotencyAdapter.reserve(idempotencyKey)) {
			Payment existing = persistenceService.findByOrderId(param.getOrderId());
			return new PaymentConfirmResponse(
				existing.getPaymentId(),
				existing.getPayStatus().name()
			);
		}

		// 외부 자동결제 실행
		TossBillingChargeResponse toss = gatewayPort.chargeWithBillingKey(
			param.getBillingKey(), param.getCustomerKey(), param.getAmount(),
			param.getOrderId(), param.getOrderName(), param.getCustomerEmail(),
			param.getCustomerName(), param.getTaxFreeAmount(), param.getTaxExemptionAmount()
		);
		// DB 결과 저장 및 실패 시 보상 로직 실행
		return retryableService.saveAutoCharge(param.getBillingKey(), param.getOrderId(), param.getAmount(), toss);
	}
}