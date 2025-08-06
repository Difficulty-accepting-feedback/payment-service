package com.grow.payment_service.subscription.infra.batch;

import java.time.LocalDateTime;
import java.util.List;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

import com.grow.payment_service.subscription.application.service.SubscriptionHistoryApplicationService;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionHistoryJpaEntity;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionStatus;
import com.grow.payment_service.subscription.infra.persistence.repository.SubscriptionHistoryJpaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryJob implements Job {

	private final SubscriptionHistoryJpaRepository historyJpaRepository;
	private final SubscriptionHistoryApplicationService historyService;

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		LocalDateTime now = LocalDateTime.now();
		List<SubscriptionHistoryJpaEntity> expiredList =
			historyJpaRepository.findExpiredBefore(SubscriptionStatus.ACTIVE, now);

		for (SubscriptionHistoryJpaEntity e : expiredList) {
			historyService.recordExpiry(
				e.getMemberId(),
				e.getStartAt(),
				e.getEndAt(),
				now
			);
			log.info("[구독 만료 스케줄러] 만료 처리: memberId={}, endAt={}",
				e.getMemberId(), e.getEndAt());
		}
	}
}