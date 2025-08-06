package com.grow.payment_service.subscription.infra.persistence.entity;

import java.time.LocalDateTime;

import com.grow.payment_service.plan.domain.model.enums.PlanPeriod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "subscriptionHistory")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "subscriptionHistoryId", nullable = false, updatable = false)
    private Long subscriptionHistoryId;

    @Column(name = "memberId", nullable = false, updatable = false)
    private Long memberId; //

    @Enumerated(EnumType.STRING)
    @Column(name = "subscriptionStatus", nullable = false, updatable = false)
    private SubscriptionStatus subscriptionStatus; // 구독 상태

    @Column(nullable = false, updatable = false)
    private PlanPeriod period;

    @Column(name = "startAt",  nullable = false, updatable = false)
    private LocalDateTime startAt; // 구독 시작 날짜

    @Column(name = "endAt",  nullable = false, updatable = false)
    private LocalDateTime endAt; // 구독 만료 날짜

    @Column(name = "changeAt")
    private LocalDateTime changeAt; // 구독 상태 변경 날짜 (해지 날짜)

    @Builder
    public SubscriptionHistoryJpaEntity(
        Long memberId,
        SubscriptionStatus subscriptionStatus,
        PlanPeriod period,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime changeAt
    ) {
        this.memberId           = memberId;
        this.subscriptionStatus = subscriptionStatus;
        this.period             = period;
        this.startAt            = startAt;
        this.endAt              = endAt;
        this.changeAt           = changeAt;
    }
}