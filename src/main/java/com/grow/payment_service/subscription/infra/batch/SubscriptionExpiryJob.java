package com.grow.payment_service.subscription.infra.batch;

import java.time.LocalDateTime;
import java.util.List;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.subscription.application.service.SubscriptionHistoryApplicationService;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionHistoryJpaEntity;
import com.grow.payment_service.subscription.domain.model.SubscriptionStatus;
import com.grow.payment_service.subscription.infra.persistence.repository.SubscriptionHistoryJpaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryJob implements Job {

	private final SubscriptionHistoryJpaRepository historyJpaRepository;
	private final SubscriptionHistoryApplicationService historyService;

	/**
	 * 연간 결제 구독 만료 스케줄러
	 * @param context
	 * @throws JobExecutionException
	 */
	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		LocalDateTime now = LocalDateTime.now();

		// 연간 플랜 ACTIVE 상태이면 endAt < now 즉시 만료
		List<SubscriptionHistoryJpaEntity> expiredYearly =
			historyJpaRepository.findExpiredByPeriod(
				SubscriptionStatus.ACTIVE,
				now,
				PlanPeriod.YEARLY
			);

		for (SubscriptionHistoryJpaEntity e : expiredYearly) {
			historyService.recordExpiry(
				e.getMemberId(),
				e.getPeriod(),
				e.getStartAt(),
				e.getEndAt(),
				now
			);
			log.info("[구독 만료 스케줄러] 연간 구독 만료 처리: memberId={}, endAt={}", e.getMemberId(), e.getEndAt());
		}
	}
}