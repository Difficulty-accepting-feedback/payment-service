package com.grow.payment_service.payment.subscription.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import com.grow.payment_service.global.exception.ErrorCode;
import com.grow.payment_service.global.exception.SubscriptionHistoryException;
import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.subscription.application.dto.SubscriptionHistoryResponse;
import com.grow.payment_service.subscription.application.service.SubscriptionHistoryApplicationService;
import com.grow.payment_service.subscription.domain.model.SubscriptionHistory;
import com.grow.payment_service.subscription.domain.repository.SubscriptionHistoryRepository;
import com.grow.payment_service.subscription.domain.model.SubscriptionStatus;

@DisplayName("SubscriptionHistoryApplicationService 테스트")
class SubscriptionHistoryApplicationServiceTest {

	@Mock
	private SubscriptionHistoryRepository repository;

	@InjectMocks
	private SubscriptionHistoryApplicationService service;

	@Captor
	ArgumentCaptor<SubscriptionHistory> historyCaptor;

	private static final Long MEMBER_ID = 777L;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Test
	@DisplayName("getMySubscriptionHistories: 구독 이력 존재 시 DTO 리스트 반환")
	void getMySubscriptionHistories_withData() {
		// given
		LocalDateTime start1  = LocalDateTime.of(2025,7,1,0,0);
		LocalDateTime end1    = LocalDateTime.of(2025,7,31,23,59);
		LocalDateTime change1 = LocalDateTime.of(2025,7,15,12,0);

		SubscriptionHistory h1 = SubscriptionHistory.of(
			10L, MEMBER_ID, SubscriptionStatus.ACTIVE, PlanPeriod.MONTHLY,
			start1, end1, change1
		);

		LocalDateTime start2  = LocalDateTime.of(2025,8,1,0,0);
		LocalDateTime end2    = LocalDateTime.of(2025,8,31,23,59);
		LocalDateTime change2 = LocalDateTime.of(2025,8,15,12,0);

		SubscriptionHistory h2 = SubscriptionHistory.of(
			11L, MEMBER_ID, SubscriptionStatus.CANCELED, PlanPeriod.MONTHLY,
			start2, end2, change2
		);

		given(repository.findByMemberId(MEMBER_ID))
			.willReturn(List.of(h1, h2));

		// when
		List<SubscriptionHistoryResponse> resp = service.getMySubscriptionHistories(MEMBER_ID);

		// then
		assertThat(resp).hasSize(2);

		SubscriptionHistoryResponse dto1 = resp.get(0);
		assertThat(dto1.getId()).isEqualTo(10L);
		assertThat(dto1.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(dto1.getPeriod()).isEqualTo(PlanPeriod.MONTHLY);
		assertThat(dto1.getStartAt()).isEqualTo(start1);
		assertThat(dto1.getEndAt()).isEqualTo(end1);
		assertThat(dto1.getChangeAt()).isEqualTo(change1);

		SubscriptionHistoryResponse dto2 = resp.get(1);
		assertThat(dto2.getId()).isEqualTo(11L);
		assertThat(dto2.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
		assertThat(dto2.getPeriod()).isEqualTo(PlanPeriod.MONTHLY);
		assertThat(dto2.getStartAt()).isEqualTo(start2);
		assertThat(dto2.getEndAt()).isEqualTo(end2);
		assertThat(dto2.getChangeAt()).isEqualTo(change2);
	}

	@Test
	@DisplayName("getMySubscriptionHistories: 구독 이력 없으면 예외")
	void getMySubscriptionHistories_emptyThrows() {
		given(repository.findByMemberId(MEMBER_ID)).willReturn(List.of());

		SubscriptionHistoryException ex = assertThrows(
			SubscriptionHistoryException.class,
			() -> service.getMySubscriptionHistories(MEMBER_ID)
		);
		assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SUBSCRIPTION_NOT_FOUND);
	}

	@Test
	@DisplayName("recordSubscriptionRenewal: ACTIVE 상태, 1개월 연장")
	void recordSubscriptionRenewal_shouldSaveActiveHistoryWithOneMonthInterval() {
		// when
		service.recordSubscriptionRenewal(MEMBER_ID, PlanPeriod.MONTHLY);

		// then
		verify(repository).save(historyCaptor.capture());
		SubscriptionHistory saved = historyCaptor.getValue();

		assertThat(saved.getSubscriptionHistoryId()).isNull();
		assertThat(saved.getMemberId()).isEqualTo(MEMBER_ID);
		assertThat(saved.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(saved.getPeriod()).isEqualTo(PlanPeriod.MONTHLY);

		LocalDateTime start = saved.getStartAt();
		LocalDateTime end   = saved.getEndAt();
		assertThat(end).isEqualTo(start.plusMonths(1));
	}

	@Test
	@DisplayName("recordExpiry: EXPIRED 상태, 전달된 값 그대로 저장")
	void recordExpiry_shouldSaveExpiredHistoryWithGivenTimestamps() {
		// given
		LocalDateTime startAt  = LocalDateTime.of(2025, 7, 1, 0, 0);
		LocalDateTime endAt    = LocalDateTime.of(2025, 7, 31, 23, 59);
		LocalDateTime changeAt = LocalDateTime.of(2025, 7, 31, 23, 59);

		// when
		service.recordExpiry(MEMBER_ID, PlanPeriod.YEARLY, startAt, endAt, changeAt);

		// then
		verify(repository).save(historyCaptor.capture());
		SubscriptionHistory saved = historyCaptor.getValue();

		assertThat(saved.getSubscriptionHistoryId()).isNull();
		assertThat(saved.getMemberId()).isEqualTo(MEMBER_ID);
		assertThat(saved.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
		assertThat(saved.getPeriod()).isEqualTo(PlanPeriod.YEARLY);
		assertThat(saved.getStartAt()).isEqualTo(startAt);
		assertThat(saved.getEndAt()).isEqualTo(endAt);
		assertThat(saved.getChangeAt()).isEqualTo(changeAt);
	}
}