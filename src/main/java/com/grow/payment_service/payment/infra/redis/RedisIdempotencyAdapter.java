package com.grow.payment_service.payment.infra.redis;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisIdempotencyAdapter {

	private final StringRedisTemplate redisTemplate;
	private static final String PREFIX = "idempotency:";
	// 유효기간 1시간
	private static final Duration TTL = Duration.ofHours(1);

	/**
	 * 중복 요청 방지를 위한 예약 메서드
	 */
	public boolean reserve(String idempotencyKey) {
		Boolean success = redisTemplate
			.opsForValue()
			.setIfAbsent(PREFIX + idempotencyKey, "1", TTL);
		return Boolean.TRUE.equals(success);
	}
}