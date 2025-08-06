package com.grow.payment_service.subscription.infra.persistence.repository;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;
import com.grow.payment_service.subscription.domain.model.SubscriptionHistory;
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
}