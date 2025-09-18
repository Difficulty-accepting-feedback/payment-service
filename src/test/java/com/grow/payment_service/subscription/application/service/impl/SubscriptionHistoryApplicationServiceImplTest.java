package com.grow.payment_service.subscription.application.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import com.grow.payment_service.global.exception.ErrorCode;
import com.grow.payment_service.global.exception.SubscriptionHistoryException;
import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.subscription.application.dto.SubscriptionHistoryResponse;
import com.grow.payment_service.subscription.application.dto.SubscriptionHistorySummary;
import com.grow.payment_service.subscription.domain.model.SubscriptionHistory;
import com.grow.payment_service.subscription.domain.repository.SubscriptionHistoryRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionHistoryApplicationServiceImplTest {

	@Mock
	SubscriptionHistoryRepository repository;

	@InjectMocks
	SubscriptionHistoryApplicationServiceImpl service;

	@Test
	@DisplayName("getMySubscriptionHistories: 이력이 존재하면 DTO 리스트 반환")
	void getMySubscriptionHistories_ok() {
		// given: 도메인 히스토리 2개 목킹
		SubscriptionHistory h1 = mock(SubscriptionHistory.class);
		SubscriptionHistory h2 = mock(SubscriptionHistory.class);
		given(repository.findByMemberId(10L)).willReturn(List.of(h1, h2));

		// when
		List<SubscriptionHistoryResponse> list = service.getMySubscriptionHistories(10L);

		// then
		assertNotNull(list);
		assertEquals(2, list.size());
		then(repository).should(times(1)).findByMemberId(10L);
	}

	@Test
	@DisplayName("getMySubscriptionHistories: 이력이 없으면 SUBSCRIPTION_NOT_FOUND")
	void getMySubscriptionHistories_empty_throw() {
		given(repository.findByMemberId(10L)).willReturn(List.of());

		SubscriptionHistoryException ex = assertThrows(
			SubscriptionHistoryException.class,
			() -> service.getMySubscriptionHistories(10L)
		);
		assertEquals(ErrorCode.SUBSCRIPTION_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("getSubscriptionSummaries: start/end 동일 그룹별로 가장 최신(changeAt|startAt)만 요약")
	void getSubscriptionSummaries_grouping_picksLatestPerGroup() {
		// group A: 같은 기간, changeAt 서로 다름
		LocalDateTime sA = LocalDateTime.of(2025, 8, 1, 0, 0);
		LocalDateTime eA = LocalDateTime.of(2025, 9, 1, 0, 0);

		SubscriptionHistory a1 = mock(SubscriptionHistory.class);
		given(a1.getMemberId()).willReturn(10L);
		given(a1.getStartAt()).willReturn(sA);
		given(a1.getEndAt()).willReturn(eA);
		given(a1.getChangeAt()).willReturn(LocalDateTime.of(2025, 8, 5, 0, 0));
		// 상태 타입은 구체 enum을 몰라도 null 허용 가능 (요약 생성 시 null 허용)
		given(a1.getSubscriptionStatus()).willReturn(null);

		SubscriptionHistory a2 = mock(SubscriptionHistory.class);
		given(a2.getMemberId()).willReturn(10L);
		given(a2.getStartAt()).willReturn(sA);
		given(a2.getEndAt()).willReturn(eA);
		given(a2.getChangeAt()).willReturn(LocalDateTime.of(2025, 8, 10, 0, 0)); // 최신
		given(a2.getSubscriptionStatus()).willReturn(null);

		// group B
		LocalDateTime sB = LocalDateTime.of(2025, 9, 1, 0, 0);
		LocalDateTime eB = LocalDateTime.of(2025, 10, 1, 0, 0);

		SubscriptionHistory b1 = mock(SubscriptionHistory.class);
		given(b1.getMemberId()).willReturn(10L);
		given(b1.getStartAt()).willReturn(sB);
		given(b1.getEndAt()).willReturn(eB);
		given(b1.getChangeAt()).willReturn(null); // changeAt 없으면 startAt 사용
		given(b1.getSubscriptionStatus()).willReturn(null);

		given(repository.findByMemberId(10L)).willReturn(List.of(a1, a2, b1));

		// when
		List<SubscriptionHistorySummary> summaries = service.getSubscriptionSummaries(10L);

		// then
		assertEquals(2, summaries.size());
		// 정렬은 보장하지 않으므로 포함 여부/매칭만 확인
		boolean hasA = summaries.stream().anyMatch(s ->
			s.getStartAt().equals(sA) && s.getEndAt().equals(eA));
		boolean hasB = summaries.stream().anyMatch(s ->
			s.getStartAt().equals(sB) && s.getEndAt().equals(eB));
		assertTrue(hasA);
		assertTrue(hasB);
		then(repository).should().findByMemberId(10L);
	}

	@Test
	@DisplayName("getSubscriptionSummaries: 이력이 없으면 SUBSCRIPTION_NOT_FOUND")
	void getSubscriptionSummaries_empty_throw() {
		given(repository.findByMemberId(10L)).willReturn(List.of());

		SubscriptionHistoryException ex = assertThrows(
			SubscriptionHistoryException.class,
			() -> service.getSubscriptionSummaries(10L)
		);
		assertEquals(ErrorCode.SUBSCRIPTION_NOT_FOUND, ex.getErrorCode());
	}

	@Test
	@DisplayName("recordSubscriptionRenewal: 저장 1회 호출")
	void recordSubscriptionRenewal_saves() {
		service.recordSubscriptionRenewal(10L, PlanPeriod.MONTHLY);
		then(repository).should(times(1)).save(any(SubscriptionHistory.class));
	}

	@Test
	@DisplayName("recordExpiry: 저장 1회 호출")
	void recordExpiry_saves() {
		LocalDateTime s = LocalDateTime.of(2025, 8, 1, 0, 0);
		LocalDateTime e = LocalDateTime.of(2025, 9, 1, 0, 0);
		LocalDateTime c = LocalDateTime.of(2025, 8, 31, 23, 59);

		service.recordExpiry(10L, PlanPeriod.MONTHLY, s, e, c);
		then(repository).should(times(1)).save(any(SubscriptionHistory.class));
	}
}