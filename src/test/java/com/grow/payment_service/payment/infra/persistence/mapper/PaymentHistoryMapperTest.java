package com.grow.payment_service.payment.infra.persistence.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.grow.payment_service.payment.domain.model.PaymentHistory;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;
import com.grow.payment_service.payment.infra.persistence.entity.PaymentHistoryJpaEntity;

@DisplayName("PaymentHistoryMapper 단위 테스트")
class PaymentHistoryMapperTest {

	@Test
	@DisplayName("toDomain(): JPA 엔티티 → 도메인 매핑")
	void toDomain_mapsAllFields() {
		// given
		Long historyId   = 10L;
		Long paymentId   = 99L;
		PayStatus status = PayStatus.DONE;
		LocalDateTime changedAt = LocalDateTime.of(2025, 8, 4, 22, 30);
		String reason    = "자동결제 승인";

		PaymentHistoryJpaEntity entity = PaymentHistoryJpaEntity.builder()
			.paymentHistoryId(historyId)
			.paymentId(paymentId)
			.status(status)
			.changedAt(changedAt)
			.reasonDetail(reason)
			.build();

		// when
		PaymentHistory domain = PaymentHistoryMapper.toDomain(entity);

		// then
		assertAll("엔티티의 각 필드가 도메인 객체로 정확히 복사되어야 한다",
			() -> assertEquals(historyId,   domain.getPaymentHistoryId()),
			() -> assertEquals(paymentId,   domain.getPaymentId()),
			() -> assertEquals(status,      domain.getStatus()),
			() -> assertEquals(changedAt,   domain.getChangedAt()),
			() -> assertEquals(reason,      domain.getReasonDetail())
		);
	}

	@Test
	@DisplayName("toEntity(): 도메인 → JPA 엔티티 매핑")
	void toEntity_mapsAllFields() {
		// given
		Long historyId   = 20L;
		Long paymentId   = 77L;
		PayStatus status = PayStatus.CANCELLED;
		LocalDateTime changedAt = LocalDateTime.of(2025, 8, 4, 23, 45);
		String reason    = "사용자 취소";

		PaymentHistory domain = PaymentHistory.of(
			historyId,
			paymentId,
			status,
			changedAt,
			reason
		);

		// when
		PaymentHistoryJpaEntity entity = PaymentHistoryMapper.toEntity(domain);

		// then
		assertAll("도메인 객체의 각 필드가 JPA 엔티티로 정확히 복사되어야 한다",
			() -> assertEquals(historyId,   entity.getPaymentHistoryId()),
			() -> assertEquals(paymentId,   entity.getPaymentId()),
			() -> assertEquals(status,      entity.getStatus()),
			() -> assertEquals(changedAt,   entity.getChangedAt()),
			() -> assertEquals(reason,      entity.getReasonDetail())
		);
	}
}