package com.grow.payment_service.payment.domain.model;

import static com.grow.payment_service.payment.domain.model.enums.PayStatus.*;

import java.util.EnumSet;

import com.grow.payment_service.payment.domain.exception.PaymentDomainException;
import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.model.enums.FailureReason;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;

import lombok.Getter;

@Getter
public class Payment {
	private final Long paymentId;
	private final Long memberId;
	private final Long planId;
	private final String orderId;
	private final String paymentKey;
	private final String billingKey;
	private final String customerKey;
	private final Long totalAmount;
	private final PayStatus payStatus;
	private final String method;
	private final FailureReason failureReason;
	private final CancelReason cancelReason;

	public Payment(Long paymentId, Long memberId, Long planId, String orderId, String paymentKey, String billingKey,
			String customerKey, Long totalAmount, PayStatus payStatus, String method, FailureReason failureReason,
			CancelReason cancelReason) {
		this.paymentId = paymentId;
		this.memberId = memberId;
		this.planId = planId;
		this.orderId = orderId;
		this.paymentKey = paymentKey;
		this.billingKey = billingKey;
		this.customerKey = customerKey;
		this.totalAmount = totalAmount;
		this.payStatus = payStatus;
		this.method = method;
		this.failureReason = failureReason;
		this.cancelReason = cancelReason;
	}

	public static Payment create(Long memberId, Long planId, String orderId,
		String paymentKey, String billingKey, String customerKey,
		Long totalAmount, String method) {
		return new Payment(
			null,
			memberId,
			planId,
			orderId,
			paymentKey,
			billingKey,
			customerKey,
			totalAmount,
			PayStatus.READY,
			method,
			null,
			null
		);
	}

	public Payment transitionTo(PayStatus nextStatus) {
		if (!this.payStatus.canTransitionTo(nextStatus)) {
			throw PaymentDomainException.invalidStatusTransition(this.payStatus, nextStatus);
		}
		return new Payment(
			paymentId, memberId, planId, orderId,
			paymentKey, billingKey, customerKey,
			totalAmount, nextStatus, method,
			this.failureReason, this.cancelReason
		);
	}


	/** 취소 요청 상태로 전이, cancelReason 세팅 */
	public Payment requestCancel(CancelReason reason) {
		if (!this.payStatus.canTransitionTo(CANCEL_REQUESTED)) {
			throw PaymentDomainException.invalidStatusTransition(this.payStatus, CANCEL_REQUESTED);
		}
		return new Payment(
			paymentId, memberId, planId, orderId,
			paymentKey, billingKey, customerKey,
			totalAmount, PayStatus.CANCEL_REQUESTED, method,
			failureReason, reason
		);
	}

	/** 취소 완료 상태로 전이 */
	public Payment completeCancel() {
		if (!this.payStatus.canTransitionTo(CANCELLED)) {
			throw PaymentDomainException.invalidStatusTransition(this.payStatus, CANCELLED);
		}
		return new Payment(
			paymentId, memberId, planId, orderId,
			paymentKey, billingKey, customerKey,
			totalAmount, PayStatus.CANCELLED, method,
			failureReason, this.cancelReason
		);
	}

	/** 빌링키 등록 후 상태 전이 */
	public Payment registerBillingKey(String billingKey) {
		if (this.payStatus != READY)
			throw PaymentDomainException.invalidStatusTransition(this.payStatus, AUTO_BILLING_READY);
		return new Payment(
			paymentId, memberId, planId, orderId,
			paymentKey, billingKey, customerKey,
			totalAmount, PayStatus.AUTO_BILLING_READY,
			method, failureReason, cancelReason
		);
	}

	/** 자동결제 승인 후 상태 전이 */
	public Payment approveAutoBilling() {
		if (!this.payStatus.canTransitionTo(AUTO_BILLING_APPROVED))
			throw PaymentDomainException.invalidStatusTransition(this.payStatus, AUTO_BILLING_APPROVED);
		return new Payment(
			paymentId, memberId, planId, orderId,
			paymentKey, billingKey, customerKey,
			totalAmount, PayStatus.AUTO_BILLING_APPROVED,
			method, failureReason, cancelReason
		);
	}

	/** 자동결제 실패 시 상태 전이 */
	public Payment failAutoBilling(FailureReason reason) {
		if (!this.payStatus.canTransitionTo(AUTO_BILLING_FAILED))
			throw PaymentDomainException.invalidStatusTransition(this.payStatus, AUTO_BILLING_FAILED);
		return new Payment(
			paymentId, memberId, planId, orderId,
			paymentKey, billingKey, customerKey,
			totalAmount, PayStatus.AUTO_BILLING_FAILED,
			method, reason, cancelReason
		);
	}


    /** 자동결제 중단 -> 빌링 키 제거 */
	public Payment clearBillingKey() {
		if (!EnumSet.of(PayStatus.AUTO_BILLING_READY, PayStatus.AUTO_BILLING_IN_PROGRESS, PayStatus.AUTO_BILLING_FAILED)
			.contains(this.payStatus)) {
			throw PaymentDomainException.invalidStatusTransition(this.payStatus, PayStatus.ABORTED);
		}
		// 2) 빌링키 제거 + 상태 전이
		return new Payment(
			this.paymentId,
			this.memberId,
			this.planId,
			this.orderId,
			this.paymentKey,
			null,
			this.customerKey,
			this.totalAmount,
			PayStatus.ABORTED,
			this.method,
			null,
			this.cancelReason
		);
	}

	/**
	 * 자동결제 준비 상태에서 자동결제 진행 중으로 전이
	 */
	public Payment startAutoBilling() {
		if (!this.payStatus.canTransitionTo(PayStatus.AUTO_BILLING_IN_PROGRESS)) {
			throw PaymentDomainException.invalidStatusTransition(this.payStatus, PayStatus.AUTO_BILLING_IN_PROGRESS);
		}
		return new Payment(
			paymentId, memberId, planId, orderId,
			paymentKey, billingKey, customerKey,
			totalAmount, PayStatus.AUTO_BILLING_IN_PROGRESS,
			method, failureReason, cancelReason
		);
	}

	/** 강제 취소 상태 전이 */
	public Payment forceCancel(CancelReason reason) {
		return new Payment(
			paymentId, memberId, planId, orderId,
			paymentKey, billingKey, customerKey,
			totalAmount, PayStatus.CANCELLED,
			method, failureReason, reason
		);
	}


	/** 자동결제 승인 후 다음 달 자동결제를 위해 READY 상태로 리셋 */
	public Payment resetForNextCycle() {
		if (this.payStatus != PayStatus.AUTO_BILLING_APPROVED) {
			throw PaymentDomainException.invalidStatusTransition(this.payStatus, AUTO_BILLING_READY);
		}
		return new Payment(
			paymentId,
			memberId,
			planId,
			orderId,
			paymentKey,
			billingKey,
			customerKey,
			totalAmount,
			AUTO_BILLING_READY,   // 다시 준비 상태
			method,
			null,                 // 실패 사유 초기화
			null                  // 취소 사유 초기화
		);
	}

	/** 호출한 memberId와 일치하지 않으면 예외 */
	public void verifyOwnership(Long memberId) {
		if (!this.memberId.equals(memberId)) {
			throw new PaymentDomainException("ACCESS_DENIED");
		}
	}

	public static Payment of(Long paymentId, Long memberId, Long planId, String orderId,
		String paymentKey, String billingKey, String customerKey,
		Long totalAmount, PayStatus payStatus, String method,
		FailureReason failureReason, CancelReason cancelReason) {
		return new Payment(paymentId, memberId, planId, orderId, paymentKey, billingKey,
			customerKey, totalAmount, payStatus, method, failureReason, cancelReason);
	}
}