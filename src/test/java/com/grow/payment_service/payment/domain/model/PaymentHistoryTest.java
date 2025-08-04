package com.grow.payment_service.payment.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.grow.payment_service.payment.domain.model.enums.PayStatus;

@DisplayName("PaymentHistory 도메인 모델 테스트")
class PaymentHistoryTest {

	private static final Long PAYMENT_ID = 1L;
	private static final PayStatus STATUS = PayStatus.READY;
	private static final String REASON = "주문 생성";

	@Test
	@DisplayName("create() 호출 시 paymentHistoryId는 null, changedAt는 현재 시각 범위 내, 나머지 필드는 입력값과 동일")
	void create_nowAndFields() {
		LocalDateTime before = LocalDateTime.now();

		PaymentHistory history = PaymentHistory.create(PAYMENT_ID, STATUS, REASON);

		LocalDateTime after = LocalDateTime.now();

		assertAll("create() 결과 검증",
			() -> assertNull(history.getPaymentHistoryId(), "paymentHistoryId는 null이어야 한다"),
			() -> assertEquals(PAYMENT_ID, history.getPaymentId(), "paymentId가 입력값과 일치해야 한다"),
			() -> assertEquals(STATUS, history.getStatus(), "status가 입력값과 일치해야 한다"),
			() -> assertEquals(REASON, history.getReasonDetail(), "reasonDetail이 입력값과 일치해야 한다"),
			() -> assertNotNull(history.getChangedAt(), "changedAt은 null이 아니어야 한다"),
			() -> assertFalse(history.getChangedAt().isBefore(before), "changedAt은 before 이후여야 한다"),
			() -> assertFalse(history.getChangedAt().isAfter(after), "changedAt은 after 이전이어야 한다")
		);
	}

	@Test
	@DisplayName("of() 호출 시 모든 필드가 주어진 값으로 설정된다")
	void of_givenAllFields() {
		Long historyId = 10L;
		LocalDateTime customTime = LocalDateTime.of(2025, 8, 4, 21, 0);

		PaymentHistory history = PaymentHistory.of(
			historyId,
			PAYMENT_ID,
			STATUS,
			customTime,
			REASON
		);

		assertAll("of() 결과 검증",
			() -> assertEquals(historyId,       history.getPaymentHistoryId(), "paymentHistoryId가 입력값과 일치해야 한다"),
			() -> assertEquals(PAYMENT_ID,      history.getPaymentId(),        "paymentId가 입력값과 일치해야 한다"),
			() -> assertEquals(STATUS,          history.getStatus(),           "status가 입력값과 일치해야 한다"),
			() -> assertEquals(customTime,      history.getChangedAt(),        "changedAt이 입력값과 일치해야 한다"),
			() -> assertEquals(REASON,          history.getReasonDetail(),     "reasonDetail이 입력값과 일치해야 한다")
		);
	}
}