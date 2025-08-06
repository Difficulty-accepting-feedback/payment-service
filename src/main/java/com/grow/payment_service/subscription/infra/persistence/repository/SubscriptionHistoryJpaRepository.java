package com.grow.payment_service.subscription.infra.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionHistoryJpaEntity;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionStatus;

public interface SubscriptionHistoryJpaRepository extends JpaRepository<SubscriptionHistoryJpaEntity, Long> {
	List<SubscriptionHistoryJpaEntity> findAllByMemberId(Long memberId);

	@Query("""
      select e 
      from SubscriptionHistoryJpaEntity e
      where e.subscriptionStatus = :activeStatus
        and e.endAt < :now
    """)
	List<SubscriptionHistoryJpaEntity> findExpiredBefore(
		@Param("activeStatus") SubscriptionStatus activeStatus,
		@Param("now") LocalDateTime now
	);
}