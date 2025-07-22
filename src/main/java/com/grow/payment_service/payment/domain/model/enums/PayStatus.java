package com.grow.payment_service.payment.domain.model.enums;

import java.util.Map;
import java.util.Set;
import java.util.EnumSet;
import static java.util.Map.entry;

public enum PayStatus {

	// 결제 흐름
	READY("결제 요청 전송 전"),
	IN_PROGRESS("결제수단 정보와 해당 결제수단의 소유자가 맞는지 인증을 마친 상태"),
	WAITING_FOR_DEPOSIT("발급된 가상계좌에 구매자가 아직 입금하지 않은 상태"),
	DONE("인증된 결제수단으로 요청한 결제가 승인된 상태"),

	// 환불 흐름
	REFUND_REQUESTED("환불 요청됨"),
	PARTIALLY_REFUNDED("부분 환불 완료"),
	REFUNDED("환불 완료"),

	// 취소 흐름
	CANCEL_REQUESTED("취소 요청됨"),
	CANCELLED("취소 완료"),

	// 자동결제 흐름
	AUTO_BILLING_READY("자동 결제 요청 전송 전"),
	AUTO_BILLING_APPROVED("자동결제 승인 완료"),
	AUTO_BILLING_FAILED("자동결제 승인 실패"),

	// 예외·종료 상태
	ABORTED("결제 승인이 실패한 상태"),
	EXPIRED("결제 유효 시간 30분이 지나 거래가 취소된 상태"),
	FAILED("결제 실패한 상태");

	private final String description;

	PayStatus(String description) {
		this.description = description;
	}

	/**
	 * 이 상태에서 전이 가능한 다음 상태
	 */
	private static final Map<PayStatus, Set<PayStatus>> ALLOWED_TRANSITIONS;

	static {
		ALLOWED_TRANSITIONS = Map.ofEntries(
			entry(READY, EnumSet.of(DONE, IN_PROGRESS, WAITING_FOR_DEPOSIT, FAILED, EXPIRED, ABORTED)),
			entry(IN_PROGRESS, EnumSet.of(DONE, FAILED, EXPIRED, ABORTED)),
			entry(WAITING_FOR_DEPOSIT, EnumSet.of(DONE, FAILED, EXPIRED, ABORTED)),
			entry(DONE, EnumSet.of(REFUND_REQUESTED, CANCEL_REQUESTED)),
			entry(REFUND_REQUESTED, EnumSet.of(REFUNDED, PARTIALLY_REFUNDED)),
			entry(PARTIALLY_REFUNDED, EnumSet.of(REFUNDED)),
			entry(REFUNDED, EnumSet.noneOf(PayStatus.class)),
			entry(CANCEL_REQUESTED, EnumSet.of(CANCELLED)),
			entry(CANCELLED, EnumSet.noneOf(PayStatus.class)),
			entry(AUTO_BILLING_READY, EnumSet.of(AUTO_BILLING_APPROVED, AUTO_BILLING_FAILED, ABORTED)),
			entry(AUTO_BILLING_APPROVED, EnumSet.noneOf(PayStatus.class)),
			entry(AUTO_BILLING_FAILED, EnumSet.noneOf(PayStatus.class)),
			entry(ABORTED, EnumSet.noneOf(PayStatus.class)),
			entry(EXPIRED, EnumSet.noneOf(PayStatus.class)),
			entry(FAILED, EnumSet.noneOf(PayStatus.class))
		);
	}

	/**
	 * 현재 상태에서 nextStatus로 전이 가능한지 검사
	 */
	public boolean canTransitionTo(PayStatus nextStatus) {
		return ALLOWED_TRANSITIONS
			.getOrDefault(this, Set.of())
			.contains(nextStatus);
	}

	public String getDescription() {
		return description;
	}
}