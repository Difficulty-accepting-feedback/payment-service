package com.grow.payment_service.subscription.infra.persistence.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;
import com.grow.payment_service.subscription.domain.model.SubscriptionHistory;
import com.grow.payment_service.subscription.domain.model.SubscriptionStatus;
import com.grow.payment_service.subscription.domain.repository.SubscriptionHistoryRepository;
import com.grow.payment_service.subscription.infra.persistence.entity.SubscriptionHistoryJpaEntity;
import com.grow.payment_service.subscription.infra.persistence.mapper.SubscriptionHistoryMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class SubscriptionHistoryRepositoryImpl implements SubscriptionHistoryRepository {

    private final SubscriptionHistoryMapper mapper;
    private final SubscriptionHistoryJpaRepository jpaRepository;

    @Override
    public SubscriptionHistory save(SubscriptionHistory domain) {
        SubscriptionHistoryJpaEntity entity = mapper.toEntity(domain);
        return mapper.toDomain(jpaRepository.save(entity));
    }

    /**
     * 멤버 ID를 기준으로 모든 구독 내역을 가져 온다
     * @param memberId 멤버 ID
     * @return 구독 내역
     */
    @Override
    public List<SubscriptionHistory> findByMemberId(Long memberId) {
        return jpaRepository.findAllByMemberId(memberId).stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * 멤버 ID와 현재 시간 기준으로 활성화된 구독 내역이 존재하는지 확인한다
     * @param memberId 멤버 ID
     * @param now 현재 시간
     * @return 활성화된 구독 내역 존재 여부
     */
    @Override
    public boolean existsActiveAfter(Long memberId, LocalDateTime now) {
        return jpaRepository.existsByMemberIdAndSubscriptionStatusAndEndAtAfter(
            memberId, SubscriptionStatus.ACTIVE, now
        );
    }

    /**
     * 멤버 ID를 기준으로 가장 최근의 구독 내역을 가져 온다
     * @param memberId 멤버 ID
     * @return 가장 최근의 구독 내역 (없을 경우 빈 Optional)
     */
    @Override
    public Optional<SubscriptionHistory> findLatestByMemberId(Long memberId) {
        return jpaRepository.findTopByMemberIdOrderByEndAtDesc(memberId).map(mapper::toDomain);
    }
}