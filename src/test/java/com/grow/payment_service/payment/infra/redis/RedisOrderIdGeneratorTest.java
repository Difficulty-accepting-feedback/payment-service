package com.grow.payment_service.payment.infra.redis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisOrderIdGenerator 단위 테스트")
class RedisOrderIdGeneratorTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOps;

	private RedisOrderIdGenerator generator;

	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

	@BeforeEach
	void setUp() {
		when(redisTemplate.opsForValue()).thenReturn(valueOps);
		generator = new RedisOrderIdGenerator(redisTemplate);
	}

	@Test
	@DisplayName("generate(): 첫 호출 시 increment=1 → expire 호출, 결과 'YYYYMMDD+memberId+0001'")
	void generate_firstSequence_expireAndPadded() {
		Long memberId = 42L;
		String today = LocalDate.now().format(DATE_FMT);
		String key = "orderId:" + today + ":" + memberId;

		when(valueOps.increment(eq(key))).thenReturn(1L);

		String result = generator.generate(memberId);

		// 반환값 검증
		assertEquals(today + memberId + "0001", result);

		// expire 호출 검증 (Duration.ofDays(1))
		verify(redisTemplate).expire(eq(key), eq(Duration.ofDays(1)));
	}

	@Test
	@DisplayName("generate(): 두번째 호출 시 increment>1 → expire 미호출, 결과 날짜+memberId+4자리 패딩")
	void generate_subsequentSequence_noExpireAndPadded() {
		Long memberId = 99L;
		String today = LocalDate.now().format(DATE_FMT);
		String key = "orderId:" + today + ":" + memberId;

		when(valueOps.increment(eq(key))).thenReturn(27L);

		String result = generator.generate(memberId);

		assertEquals(today + memberId + "0027", result);

		verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
	}

	@Test
	@DisplayName("generate(): increment 반환이 null 이어도 NPE 없이 padded 결과 생성")
	void generate_nullSequence_fallbackToNullPointer() {
		Long memberId = 7L;
		String today = LocalDate.now().format(DATE_FMT);
		String key = "orderId:" + today + ":" + memberId;

		when(valueOps.increment(eq(key))).thenReturn(null);

		// null 시 String.format에 null 넣으면 "null" 문자열이 붙음
		String result = generator.generate(memberId);

		assertEquals(today + memberId + "null", result);

		// expire는 seq==null 이므로 호출되지 않음
		verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
	}
}