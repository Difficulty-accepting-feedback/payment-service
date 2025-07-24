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
	private final Long orderId;
	private final String paymentKey;
	private final Long billingKey;
	private final String customerKey;
	private final Long totalAmount;
	private final PayStatus payStatus;
	private final String method;
	private final FailureReason failureReason;
	private final CancelReason cancelReason;

	public Payment(Long paymentId, Long memberId, Long planId, Long orderId, String paymentKey, Long billingKey,
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

	public static Payment create(Long memberId, Long planId, Long orderId,
		String paymentKey, Long billingKey, String customerKey,
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


	public static Payment of(Long paymentId, Long memberId, Long planId, Long orderId,
		String paymentKey, Long billingKey, String customerKey,
		Long totalAmount, PayStatus payStatus, String method,
		FailureReason failureReason, CancelReason cancelReason) {
		return new Payment(paymentId, memberId, planId, orderId, paymentKey, billingKey,
			customerKey, totalAmount, payStatus, method, failureReason, cancelReason);
	}
}