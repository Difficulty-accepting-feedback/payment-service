package com.grow.payment_service.payment.infra.redis;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisIdempotencyAdapter {

	private final StringRedisTemplate redisTemplate;
	private static final String PREFIX = "idempotency:";
	// 유효 기간 15일
	private static final Duration TTL = Duration.ofDays(15);

	/**
	 * 중복 요청 방지를 위한 예약 메서드
	 */
	public boolean reserve(String idempotencyKey) {
		Boolean success = redisTemplate
			.opsForValue()
			.setIfAbsent(PREFIX + idempotencyKey, "1", TTL);
		return Boolean.TRUE.equals(success);
	}

	/**
	 * 멱등성 키를 생성하고, 해당 키가 이미 존재하는지 확인합니다. (자동결제에서 사용)
	 */
	public String getOrCreateKey(String keyRecord) {
		String redisKey = PREFIX + "record:" + keyRecord;
		// 새로운 UUID 키 생성
		String newKey = UUID.randomUUID().toString();

		// setIfAbsent 으로 한 번만 저장 시도
		Boolean created = redisTemplate
			.opsForValue()
			.setIfAbsent(redisKey, newKey, TTL);

		if (Boolean.TRUE.equals(created)) {
			return newKey;
		}

		// 이미 다른 쓰레드가 생성했으면, 그 키를 가져옴
		String existing = redisTemplate.opsForValue().get(redisKey);
		return existing != null ? existing : newKey;
	}
}