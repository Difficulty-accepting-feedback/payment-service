package com.grow.payment_service.subscription.domain.repository;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.grow.payment_service.subscription.domain.model.SubscriptionHistory;

public interface SubscriptionHistoryRepository {
    SubscriptionHistory save(SubscriptionHistory domain);
    List<SubscriptionHistory> findByMemberId(Long memberId);
    boolean existsActiveAfter(Long memberId, LocalDateTime now);
    Optional<SubscriptionHistory> findLatestByMemberId(Long memberId);
}