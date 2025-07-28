package com.grow.payment_service.payment.domain.model;

import static com.grow.payment_service.payment.domain.model.enums.PayStatus.*;

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
			throw new IllegalStateException(
				this.payStatus + " → " + nextStatus + " 전이 불가"
			);
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
			throw new IllegalStateException(payStatus + "에서 취소 요청 전이 불가");
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
			throw new IllegalStateException(payStatus + "에서 취소 완료 전이 불가");
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
		if (!this.payStatus.canTransitionTo(AUTO_BILLING_READY))
			throw new IllegalStateException(payStatus + "에서 자동결제 준비 상태 전이 불가");
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
			throw new IllegalStateException(payStatus + "에서 자동결제 승인 상태 전이 불가");
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
			throw new IllegalStateException(payStatus + "에서 자동결제 실패 상태 전이 불가");
		return new Payment(
			paymentId, memberId, planId, orderId,
			paymentKey, billingKey, customerKey,
			totalAmount, PayStatus.AUTO_BILLING_FAILED,
			method, reason, cancelReason
		);
	}


    /** 자동결제 중단 -> 빌링 키 제거 */
	public Payment clearBillingKey() {
		return new Payment(
			this.paymentId,
			this.memberId,
			this.planId,
			this.orderId,
			this.paymentKey,
			null,               // billingKey 제거
			this.customerKey,
			this.totalAmount,
			this.payStatus,
			this.method,
			this.failureReason,
			this.cancelReason
		);
	}

	/** 결제 승인 후 취소 상태 전이 */
	public Payment forceCancel(CancelReason reason) {
		return new Payment(
			paymentId, memberId, planId, orderId,
			paymentKey, billingKey, customerKey,
			totalAmount, PayStatus.CANCELLED,
			method, failureReason, reason
		);
	}

	public static Payment of(Long paymentId, Long memberId, Long planId, String orderId,
		String paymentKey, String billingKey, String customerKey,
		Long totalAmount, PayStatus payStatus, String method,
		FailureReason failureReason, CancelReason cancelReason) {
		return new Payment(paymentId, memberId, planId, orderId, paymentKey, billingKey,
			customerKey, totalAmount, payStatus, method, failureReason, cancelReason);
	}
}