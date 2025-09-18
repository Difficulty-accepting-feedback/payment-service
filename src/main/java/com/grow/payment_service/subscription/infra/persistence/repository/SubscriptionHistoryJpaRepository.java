package com.grow.payment_service.subscription.infra.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionHistoryJpaEntity;
import com.grow.payment_service.subscription.domain.model.SubscriptionStatus;

public interface SubscriptionHistoryJpaRepository extends JpaRepository<SubscriptionHistoryJpaEntity, Long> {
	List<SubscriptionHistoryJpaEntity> findAllByMemberId(Long memberId);
	/**
	 * 연간 플랜 ACTIVE 상태면 endAt<now 즉시 만료
	 */
	@Query("""
      select e
        from SubscriptionHistoryJpaEntity e
       where e.subscriptionStatus = :status
         and e.endAt < :now
         and e.period = :period
    """)
	List<SubscriptionHistoryJpaEntity> findExpiredByPeriod(
		@Param("status") SubscriptionStatus status,
		@Param("now")    LocalDateTime now,
		@Param("period") PlanPeriod period
	);

	boolean existsByMemberIdAndSubscriptionStatusAndEndAtAfter(
		Long memberId, SubscriptionStatus status, LocalDateTime endAtExclusive
	);

	Optional<SubscriptionHistoryJpaEntity> findTopByMemberIdOrderByEndAtDesc(Long memberId);
}