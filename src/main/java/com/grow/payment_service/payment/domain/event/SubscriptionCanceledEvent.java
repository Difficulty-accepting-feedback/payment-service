package com.grow.payment_service.payment.domain.event;


public class SubscriptionCanceledEvent {
	private final Long memberId;
	public SubscriptionCanceledEvent(Long memberId) {
		this.memberId = memberId;
	}
	public Long getMemberId() {
		return memberId;
	}
}