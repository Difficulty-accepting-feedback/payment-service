package com.grow.payment_service.payment.domain.exception;

import com.grow.payment_service.payment.domain.model.enums.PayStatus;

public class PaymentDomainException extends RuntimeException {
	public PaymentDomainException(String message) {
		super(message);
	}

	public static PaymentDomainException invalidStatusTransition(PayStatus from, PayStatus to) {
		return new PaymentDomainException(
			String.format("Payment 상태 전이 불가: %s → %s", from, to)
		);
	}

	public static PaymentDomainException alreadyCancelled(String orderId) {
		return new PaymentDomainException(
			String.format("이미 취소된 주문입니다. orderId=%s", orderId)
		);
	}

	public static PaymentDomainException insufficientAmount(long required, long actual) {
		return new PaymentDomainException(
			String.format("금액이 충분하지 않습니다. 필요=%d, 현재=%d", required, actual)
		);
	}
	public static PaymentDomainException accessDenied(Long memberId) {
		return new PaymentDomainException(
			String.format("접근 권한이 없습니다. memberId=%d", memberId)
		);
	}
}