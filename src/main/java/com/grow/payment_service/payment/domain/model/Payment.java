package com.grow.payment_service.payment.domain.model;

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

	public Payment markInProgress() {
		return new Payment(paymentId, memberId, planId, orderId, paymentKey, billingKey,
			customerKey, totalAmount, PayStatus.IN_PROGRESS, method, null, null);
	}

	public Payment markWaitingForDeposit() {
		return new Payment(paymentId, memberId, planId, orderId, paymentKey, billingKey,
			customerKey, totalAmount, PayStatus.WAITING_FOR_DEPOSIT, method, null, null);
	}

	public Payment markDone() {
		return new Payment(paymentId, memberId, planId, orderId, paymentKey, billingKey,
			customerKey, totalAmount, PayStatus.DONE, method, null, null);
	}

	public Payment markAutoBillingReady() {
		return new Payment(paymentId, memberId, planId, orderId, paymentKey, billingKey,
			customerKey, totalAmount, PayStatus.AUTO_BILLING_READY, method, null, null);
	}

	public Payment markAutoBillingPrepared() {
		return new Payment(paymentId, memberId, planId, orderId, paymentKey, billingKey,
			customerKey, totalAmount, PayStatus.AUTO_BILLING_PREPARED, method, null, null);
	}

	public Payment markAutoBillingApproved() {
		return new Payment(paymentId, memberId, planId, orderId, paymentKey, billingKey,
			customerKey, totalAmount, PayStatus.AUTO_BILLING_APPROVED, method, null, null);
	}

	public Payment markAutoBillingFailed(FailureReason failureReason) {
		return new Payment(paymentId, memberId, planId, orderId, paymentKey, billingKey,
			customerKey, totalAmount, PayStatus.AUTO_BILLING_FAILED, method, failureReason, null);
	}

	public Payment markAborted(FailureReason reason) {
		return new Payment(paymentId, memberId, planId, orderId, paymentKey, billingKey,
			customerKey, totalAmount, PayStatus.ABORTED, method, reason, null);
	}

	public Payment markExpired() {
		return new Payment(paymentId, memberId, planId, orderId, paymentKey, billingKey,
			customerKey, totalAmount, PayStatus.EXPIRED, method, null, null);
	}

	public Payment markFailed(FailureReason reason) {
		return new Payment(paymentId, memberId, planId, orderId, paymentKey, billingKey,
			customerKey, totalAmount, PayStatus.FAILED, method, reason, null);
	}

	public static Payment of(Long paymentId, Long memberId, Long planId, Long orderId,
		String paymentKey, Long billingKey, String customerKey,
		Long totalAmount, PayStatus payStatus, String method,
		FailureReason failureReason, CancelReason cancelReason) {
		return new Payment(paymentId, memberId, planId, orderId, paymentKey, billingKey,
			customerKey, totalAmount, payStatus, method, failureReason, cancelReason);
	}
}