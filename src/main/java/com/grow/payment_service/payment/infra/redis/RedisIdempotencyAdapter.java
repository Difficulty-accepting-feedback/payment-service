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
	// reserve 키 접두사, 첫 요청이 들어왔을 때 이 키를 생성해 처리중 표시를 하는 역할
	private static final String PREFIX_REC = "idempotency:rec:";
	// result 키 접두사, 처리 완료 재 요청 시 이 키를 읽어 완료 상태 표시를 하는 열할
	private static final String PREFIX_RES = "idempotency:res:";
	// 유효 기간 15일
	private static final Duration TTL = Duration.ofDays(15);

	/**
	 * 중복 요청 방지를 위한 예약 메서드
	 * 최초 호출 시 true, 이미 있으면 false 반환
	 */
	public boolean reserve(String key) {
		return Boolean.TRUE.equals(
			redisTemplate.opsForValue()
				.setIfAbsent(PREFIX_REC + key, "1", TTL)
		);
	}

	/**
	 * 처리 완료 시 호출: 결과(paymentId)를 문자열로 저장
	 */
	public void finish(String key, String result) {
		redisTemplate.opsForValue()
			.set(PREFIX_RES + key, result, TTL);
	}

	/**
	 * 재요청 시, 저장된 결과가 있으면 꺼내옴
	 * (null 이면 아직 처리 중)
	 */
	public String getResult(String key) {
		return redisTemplate.opsForValue()
			.get(PREFIX_RES + key);
	}

	/** 처리 중이던 reserve·result 키 모두 삭제 */
	public void invalidate(String key) {
		redisTemplate.delete(PREFIX_REC + key);
		redisTemplate.delete(PREFIX_RES + key);
	}

	/**
	 * 멱등성 키를 생성하고, 해당 키가 이미 존재하는지 확인합니다. (자동결제에서 사용)
	 */
	public String getOrCreateKey(String keyRecord) {
		String redisKey = PREFIX_REC + "record:" + keyRecord;
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