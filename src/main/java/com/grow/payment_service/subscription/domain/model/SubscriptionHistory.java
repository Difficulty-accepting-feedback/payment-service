package com.grow.payment_service.subscription.domain.model;

import java.time.Clock;
import java.time.LocalDateTime;

import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionStatus;

import lombok.Getter;

@Getter
public class SubscriptionHistory {

    private final Long subscriptionHistoryId;
    private final Long memberId;
    private final SubscriptionStatus subscriptionStatus;
    private final PlanPeriod period;
    private final LocalDateTime startAt; // 구독 시작 날짜
    private final LocalDateTime endAt;   // 구독 만료 날짜
    private final LocalDateTime changeAt; // 구독 상태 변경 날짜 (해지 or 만료 날짜)

    // 갱신(renewal) 생성자: ACTIVE 상태
    private SubscriptionHistory(Long memberId, Clock clock, PlanPeriod period) {
        this.subscriptionHistoryId = null;
        this.memberId              = memberId;
        this.subscriptionStatus    = SubscriptionStatus.ACTIVE;
        this.period                = period;

        LocalDateTime now = LocalDateTime.now(clock);
        this.startAt = now;
        // period 가 MONTHLY 면 plusMonths(1), YEARLY 면 plusYears(1)
        this.endAt   = switch (period) {
            case MONTHLY -> now.plusMonths(1);
            case YEARLY  -> now.plusYears(1);
            default      -> throw new IllegalArgumentException("알 수 없는 구독 기간: " + period);
        };
        this.changeAt = null;
    }

    // 만료(expiry) 생성자: EXPIRED 상태
    private SubscriptionHistory(Long memberId,
        PlanPeriod period,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime changeAt) {
        this.subscriptionHistoryId = null;
        this.memberId              = memberId;
        this.subscriptionStatus    = SubscriptionStatus.EXPIRED;
        this.period                = period;
        this.startAt               = startAt;
        this.endAt                 = endAt;
        this.changeAt              = changeAt;
    }

    public SubscriptionHistory(Long subscriptionHistoryId,
        Long memberId,
        SubscriptionStatus subscriptionStatus,
        PlanPeriod period,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime changeAt) {
        this.subscriptionHistoryId = subscriptionHistoryId;
        this.memberId              = memberId;
        this.subscriptionStatus    = subscriptionStatus;
        this.period                = period;
        this.startAt               = startAt;
        this.endAt                 = endAt;
        this.changeAt              = changeAt;
    }

    /** 구독 갱신(renewal)용 팩토리 메서드 */
    public static SubscriptionHistory createRenewal(
        Long memberId,
        PlanPeriod period,
        Clock clock
    ) {
        return new SubscriptionHistory(memberId, clock, period);
    }

    /** 구독 만료(expiry)용 팩토리 메서드 */
    public static SubscriptionHistory createExpiry(
        Long memberId,
        PlanPeriod period,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime changeAt
    ) {
        return new SubscriptionHistory(memberId, period, startAt, endAt, changeAt);
    }

    public static SubscriptionHistory of(
        Long subscriptionHistoryId,
        Long memberId,
        SubscriptionStatus subscriptionStatus,
        PlanPeriod period,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime changeAt
    ) {
        return new SubscriptionHistory(
            subscriptionHistoryId,
            memberId,
            subscriptionStatus,
            period,
            startAt,
            endAt,
            changeAt
        );
    }
}