package com.grow.payment_service.payment.domain.model.enums;

import java.util.Map;
import java.util.Set;
import java.util.EnumSet;
import static java.util.Map.entry;

public enum PayStatus {

	// 주문 생성 직후
	READY("결제 요청 전송 전"),

	// 인증·진행 중
	IN_PROGRESS("결제수단 정보 인증 완료"),

	// 결제 완료
	DONE("결제 승인 완료"),

	// 취소 흐름
	CANCEL_REQUESTED("취소 요청됨"),
	CANCELLED("취소 완료"),

	// 자동결제 흐름
	AUTO_BILLING_READY("자동결제 준비 완료"),
	AUTO_BILLING_IN_PROGRESS("자동결제 진행 중"),
	AUTO_BILLING_APPROVED("자동결제 승인 완료"),
	AUTO_BILLING_FAILED("자동결제 승인 실패"),

	// 예외·종료 상태
	ABORTED("결제 중단"),
	EXPIRED("결제 만료"),
	FAILED("결제 실패");

	private final String description;

	PayStatus(String description) {
		this.description = description;
	}

	/**
	 * 이 상태에서 전이 가능한 다음 상태 맵
	 */
	private static final Map<PayStatus, Set<PayStatus>> ALLOWED_TRANSITIONS;

	static {
		ALLOWED_TRANSITIONS = Map.ofEntries(
			// READY -> 진행 가능한 모든 흐름: 인증, 승인, 실패, 만료, 중단, 자동결제 준비
			entry(READY, EnumSet.of(
				IN_PROGRESS,
				DONE,
				FAILED,
				EXPIRED,
				ABORTED,
				AUTO_BILLING_READY,
				CANCEL_REQUESTED
			)),
			// IN_PROGRESS -> 승인 또는 실패·만료·중단
			entry(IN_PROGRESS, EnumSet.of(
				DONE,
				FAILED,
				EXPIRED,
				ABORTED
			)),
			// DONE -> 취소 요청
			entry(DONE, EnumSet.of(CANCEL_REQUESTED)),
			// 취소 요청 -> 취소 완료
			entry(CANCEL_REQUESTED, EnumSet.of(CANCELLED)),
			// 취소 완료 -> 종료(전이 불가)
			entry(CANCELLED, EnumSet.noneOf(PayStatus.class)),
			// 자동결제 준비 -> 승인 or 진행 or 실패 or 중단
			entry(AUTO_BILLING_READY, EnumSet.of(
				AUTO_BILLING_IN_PROGRESS,
				AUTO_BILLING_APPROVED,
				AUTO_BILLING_FAILED,
				ABORTED
			)),
			// 자동결제 진행 중 -> 승인 or 실패 or 중단   ← 이 블록을 추가
			entry(AUTO_BILLING_IN_PROGRESS, EnumSet.of(
				AUTO_BILLING_APPROVED,
				AUTO_BILLING_FAILED,
				ABORTED
			)),
			// AUTO_BILLING_APPROVED → 다음 달 READY로 리셋
			entry(AUTO_BILLING_APPROVED, EnumSet.of(
				AUTO_BILLING_READY
			)),
			// 자동결제 승인/실패 이후 -> 종료
			entry(AUTO_BILLING_FAILED,   EnumSet.noneOf(PayStatus.class)),
			// 예외·종료 상태들 -> 더 이상의 전이 불가
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