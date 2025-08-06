package com.grow.payment_service.subscription.domain.model;

public enum SubscriptionStatus {
    ACTIVE,      // 구독 중
    EXPIRED,     // 구독 만료
    CANCELED    // 구독 취소(갱신하지 않음)
}