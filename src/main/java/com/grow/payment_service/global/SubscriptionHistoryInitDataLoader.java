package com.grow.payment_service.global;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.subscription.domain.model.SubscriptionHistory;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionHistoryJpaEntity;
import com.grow.payment_service.subscription.infra.persistence.repository.SubscriptionHistoryJpaRepository;

@Component
public class SubscriptionHistoryInitDataLoader implements ApplicationRunner {

	private final SubscriptionHistoryJpaRepository repository;

	// 월간 구독만 예제이므로 모두 MONTHLY 고정
	private static final PlanPeriod PERIOD = PlanPeriod.MONTHLY;
	private static final Clock CLOCK = Clock.systemDefaultZone();
	// memberId 1→2개, 2→3개, 3→4개, 4→2개, 5→3개 만료 이력
	private static final int[] EXPIRED_COUNTS = {2, 3, 4, 2, 3};

	public SubscriptionHistoryInitDataLoader(SubscriptionHistoryJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		List<SubscriptionHistoryJpaEntity> entities = new ArrayList<>();

		LocalDateTime now = LocalDateTime.now(CLOCK);

		for (int i = 0; i < EXPIRED_COUNTS.length; i++) {
			long memberId = i + 1;

			// 1) 활성 구독 이력 (ACTIVE)
			SubscriptionHistory active = SubscriptionHistory.createRenewal(memberId, PERIOD, CLOCK);
			entities.add(toEntity(active));

			// 2) 만료 이력들 (EXPIRED)
			int expiredCount = EXPIRED_COUNTS[i];
			for (int j = 0; j < expiredCount; j++) {
				// j=0 → 지난 한 달 (now-1M ~ now)
				LocalDateTime endAt   = now.minusMonths(j + 1);
				LocalDateTime startAt = endAt.minusMonths(1);
				SubscriptionHistory expired = SubscriptionHistory.createExpiry(
					memberId,
					PERIOD,
					startAt,
					endAt,
					endAt  // changeAt = 만료 시점
				);
				entities.add(toEntity(expired));
			}
		}

		// 한번에 저장
		repository.saveAll(entities);
	}

	private SubscriptionHistoryJpaEntity toEntity(SubscriptionHistory d) {
		return SubscriptionHistoryJpaEntity.builder()
			.memberId(d.getMemberId())
			.subscriptionStatus(d.getSubscriptionStatus())
			.period(d.getPeriod())
			.startAt(d.getStartAt())
			.endAt(d.getEndAt())
			.changeAt(d.getChangeAt())
			.build();
	}
}