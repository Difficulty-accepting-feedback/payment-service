package com.grow.payment_service.payment.subscription.infra.persistence.mapper;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.grow.payment_service.subscription.domain.model.SubscriptionHistory;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionHistoryJpaEntity;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionStatus;
import com.grow.payment_service.subscription.infra.persistence.mapper.SubscriptionHistoryMapper;

class SubscriptionHistoryMapperTest {

	private SubscriptionHistoryMapper mapper;

	@BeforeEach
	void setUp() {
		mapper = new SubscriptionHistoryMapper();
	}

	@Test
	@DisplayName("toDomain: JPA 엔티티 → 도메인 매핑")
	void toDomain() {
		// given
		Long id        = 101L;
		Long memberId  = 202L;
		SubscriptionStatus status = SubscriptionStatus.ACTIVE;
		LocalDateTime startAt  = LocalDateTime.of(2025, 7, 1, 0, 0);
		LocalDateTime endAt    = LocalDateTime.of(2025, 7, 31, 23, 59);
		LocalDateTime changeAt = LocalDateTime.of(2025, 7, 15, 12, 30);

		// 1) 엔티티 빌더로 생성 (ID는 null)
		SubscriptionHistoryJpaEntity entity = SubscriptionHistoryJpaEntity.builder()
			.memberId(memberId)
			.subscriptionStatus(status)
			.startAt(startAt)
			.endAt(endAt)
			.changeAt(changeAt)
			.build();

		// 2) Reflection으로 private 필드에 ID 주입
		ReflectionTestUtils.setField(entity, "subscriptionHistoryId", id);

		// when
		SubscriptionHistory domain = mapper.toDomain(entity);

		// then
		assertThat(domain.getSubscriptionHistoryId()).isEqualTo(id);
		assertThat(domain.getMemberId()).isEqualTo(memberId);
		assertThat(domain.getSubscriptionStatus()).isEqualTo(status);
		assertThat(domain.getStartAt()).isEqualTo(startAt);
		assertThat(domain.getEndAt()).isEqualTo(endAt);
		assertThat(domain.getChangeAt()).isEqualTo(changeAt);
	}

	@Test
	@DisplayName("toEntity: 도메인 → JPA 엔티티 매핑")
	void toEntity() {
		// given
		Long historyId = 303L;
		Long memberId  = 404L;
		SubscriptionStatus status = SubscriptionStatus.CANCELED;
		LocalDateTime startAt  = LocalDateTime.of(2025, 6, 1, 8, 0);
		LocalDateTime endAt    = LocalDateTime.of(2025, 7, 1, 8, 0);
		LocalDateTime changeAt = LocalDateTime.of(2025, 6, 15, 9, 30);

		// 도메인 객체 생성 (6-arg 생성자)
		SubscriptionHistory domain = new SubscriptionHistory(
			historyId, memberId, status, startAt, endAt, changeAt
		);

		// when
		SubscriptionHistoryJpaEntity entity = mapper.toEntity(domain);

		// then
		// 빌더로 생성된 엔티티는 ID(null)
		assertThat(entity.getSubscriptionHistoryId()).isNull();
		assertThat(entity.getMemberId()).isEqualTo(memberId);
		assertThat(entity.getSubscriptionStatus()).isEqualTo(status);
		assertThat(entity.getStartAt()).isEqualTo(startAt);
		assertThat(entity.getEndAt()).isEqualTo(endAt);
		assertThat(entity.getChangeAt()).isEqualTo(changeAt);
	}
}