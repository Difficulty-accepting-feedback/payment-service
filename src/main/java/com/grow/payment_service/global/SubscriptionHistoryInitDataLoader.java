package com.grow.payment_service.global;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.plan.domain.model.enums.PlanType;
import com.grow.payment_service.plan.infra.persistence.entity.PlanJpaEntity;
import com.grow.payment_service.plan.infra.persistence.repository.PlanJpaRepository;
import com.grow.payment_service.subscription.domain.model.SubscriptionHistory;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionHistoryJpaEntity;
import com.grow.payment_service.subscription.infra.persistence.repository.SubscriptionHistoryJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SubscriptionHistoryInitDataLoader implements ApplicationRunner {

	private final SubscriptionHistoryJpaRepository subscriptionHistoryRepo;
	private final PlanJpaRepository           planRepo;

	// 월간 구독만 예제이므로 모두 MONTHLY 고정
	private static final PlanPeriod PERIOD = PlanPeriod.MONTHLY;
	private static final Clock      CLOCK  = Clock.systemDefaultZone();
	// memberId 1→2개, 2→3개, … 만료 이력
	private static final int[]      EXPIRED_COUNTS = {2, 3, 4, 2, 3};

	public SubscriptionHistoryInitDataLoader(
		SubscriptionHistoryJpaRepository subscriptionHistoryRepo,
		PlanJpaRepository planRepo
	) {
		this.subscriptionHistoryRepo = subscriptionHistoryRepo;
		this.planRepo                = planRepo;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		initPlans();               // 1) 플랜 초기 삽입
		initSubscriptionHistory(); // 2) 구독 이력 초기 삽입
	}

	private void initPlans() {
		List<PlanJpaEntity> plans = List.of(
			PlanJpaEntity.builder()
				.type(PlanType.SUBSCRIPTION)
				.amount(10000L)
				.period(PlanPeriod.MONTHLY)
				.benefits("기본 플랜 혜택: 월간 리포트 제공")
				.build(),
			PlanJpaEntity.builder()
				.type(PlanType.SUBSCRIPTION)
				.amount(20000L)
				.period(PlanPeriod.MONTHLY)
				.benefits("스탠다드 플랜 혜택: 월간 리포트 + 전용 지원")
				.build(),
			PlanJpaEntity.builder()
				.type(PlanType.SUBSCRIPTION)
				.amount(30000L)
				.period(PlanPeriod.MONTHLY)
				.benefits("프리미엄 플랜 혜택: 모든 기능 무제한 이용")
				.build()
		);

		// 저장 및 planId 확인
		List<PlanJpaEntity> saved = planRepo.saveAll(plans);
		saved.forEach(p ->
			log.info("Initialized Plan → id={}, type={}, amount={}, period={}, benefits={}",
				p.getPlanId(), p.getType(), p.getAmount(), p.getPeriod(), p.getBenefits())
		);
	}

	private void initSubscriptionHistory() {
		List<SubscriptionHistoryJpaEntity> entities = new ArrayList<>();
		LocalDateTime now = LocalDateTime.now(CLOCK);

		for (int i = 0; i < EXPIRED_COUNTS.length; i++) {
			long memberId = i + 1;

			// 1) 활성 구독 이력 (ACTIVE)
			SubscriptionHistory active = SubscriptionHistory.createRenewal(memberId, PERIOD, CLOCK);
			entities.add(toEntity(active));

			// 2) 만료 이력 (EXPIRED)
			int expiredCount = EXPIRED_COUNTS[i];
			for (int j = 0; j < expiredCount; j++) {
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

		subscriptionHistoryRepo.saveAll(entities);
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