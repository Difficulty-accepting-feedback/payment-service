package com.grow.payment_service.payment.infra.redis;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.grow.payment_service.payment.domain.service.OrderIdGenerator;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisOrderIdGenerator implements OrderIdGenerator {
	private final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;
	private final StringRedisTemplate redis;

	@Override
	public String generate(Long memberId) {
		// 현재 날짜를 YYYYMMDD 형식으로 가져오기
		String date = LocalDate.now().format(DATE_FMT);

		// redis 키 -> orderId:date:memberId
		String key = "orderId:" + date + ":" + memberId;

		// 원자적 증가
		Long seq = redis.opsForValue().increment(key);

		// 하루 뒤 만료 (YYYYMMDD 형식이므로)
		if (seq != null && seq == 1) {
			redis.expire(key, Duration.ofDays(1));
		}
		// 4자리로 패딩
		String padded = String.format("%04d", seq);

		return date + memberId + padded;
	}
}