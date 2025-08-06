package com.grow.payment_service.subscription.domain.model;

import java.time.Clock;
import java.time.LocalDateTime;

import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionStatus;

import lombok.Getter;

@Getter
public class SubscriptionHistory {

    private final Long subscriptionHistoryId;
    private final Long memberId;
    private SubscriptionStatus subscriptionStatus; // 구독 상태
    private final LocalDateTime startAt; // 구독 시작 날짜
    private final LocalDateTime endAt; // 구독 만료 날짜
    private LocalDateTime changeAt; // 구독 상태 변경 날짜 (해지 날짜)

    public SubscriptionHistory(Long memberId,
                               Clock startAt) {
        this.subscriptionHistoryId = null;
        this.memberId = memberId;
        this.subscriptionStatus = SubscriptionStatus.ACTIVE;

        // 단일 시점의 now를 재사용해 정확히 한 달 차이로 계산
        LocalDateTime now = (startAt != null)
            ? LocalDateTime.now(startAt)
            : LocalDateTime.now();
        this.startAt = now;
        this.endAt   = now.plusMonths(1);
    }

    public SubscriptionHistory(Long subscriptionHistoryId,
                               Long memberId,
                               SubscriptionStatus subscriptionStatus,
                               LocalDateTime startAt,
                               LocalDateTime endAt,
                               LocalDateTime changeAt
    ) {
        this.subscriptionHistoryId = subscriptionHistoryId;
        this.memberId = memberId;
        this.subscriptionStatus = subscriptionStatus;
        this.startAt = startAt;
        this.endAt = endAt;
        this.changeAt = changeAt;
    }
}