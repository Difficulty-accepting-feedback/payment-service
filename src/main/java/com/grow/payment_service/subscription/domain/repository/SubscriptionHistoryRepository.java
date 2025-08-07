package com.grow.payment_service.subscription.domain.repository;


import java.util.List;

import com.grow.payment_service.subscription.domain.model.SubscriptionHistory;

public interface SubscriptionHistoryRepository {
    SubscriptionHistory save(SubscriptionHistory domain);
    List<SubscriptionHistory> findByMemberId(Long memberId);
}