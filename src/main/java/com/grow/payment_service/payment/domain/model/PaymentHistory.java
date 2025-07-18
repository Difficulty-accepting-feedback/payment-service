package com.grow.payment_service.payment.domain.model;

import java.time.LocalDateTime;

import com.grow.payment_service.payment.domain.model.enums.PayStatus;

import lombok.Getter;

@Getter
public class PaymentHistory {

	private final Long paymentHistoryId;
	private final Long paymentId;
	private final PayStatus status;
	private final LocalDateTime changedAt;
	private final String reasonDetail;

	public PaymentHistory(Long paymentHistoryId, Long paymentId, PayStatus status, LocalDateTime changedAt, String reasonDetail) {
		this.paymentHistoryId = paymentHistoryId;
		this.paymentId = paymentId;
		this.status = status;
		this.changedAt = changedAt;
		this.reasonDetail = reasonDetail;
	}

	public static PaymentHistory create(Long paymentId, PayStatus status, String reasonDetail) {
		return new PaymentHistory(
			null,
			paymentId,
			status,
			LocalDateTime.now(),
			reasonDetail
		);
	}

	public static PaymentHistory of(Long paymentHistoryId, Long paymentId,
		PayStatus status, LocalDateTime changedAt, String reasonDetail) {
		return new PaymentHistory(paymentHistoryId, paymentId, status, changedAt, reasonDetail);
	}
}