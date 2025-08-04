package com.grow.payment_service.payment.infra.redis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RedisIdempotencyAdapter 단위 테스트")
class RedisIdempotencyAdapterTest {

	@Mock(lenient = true)
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOps;

	private RedisIdempotencyAdapter adapter;

	@BeforeEach
	void setUp() {
		// lenient stub: 모든 테스트에서 opsForValue() 호출을 허용
		when(redisTemplate.opsForValue()).thenReturn(valueOps);
		adapter = new RedisIdempotencyAdapter(redisTemplate);
	}

	@Test
	@DisplayName("reserve: 신규 키 생성 시 true 반환")
	void reserve_new_returnsTrue() {
		when(valueOps.setIfAbsent(
			eq("idempotency:rec:foo"),
			eq("1"),
			eq(Duration.ofDays(15))
		)).thenReturn(true);

		assertTrue(adapter.reserve("foo"));
	}

	@Test
	@DisplayName("reserve: 이미 존재 시 false 반환")
	void reserve_existing_returnsFalse() {
		when(valueOps.setIfAbsent(
			eq("idempotency:rec:bar"),
			eq("1"),
			eq(Duration.ofDays(15))
		)).thenReturn(false);

		assertFalse(adapter.reserve("bar"));
	}

	@Test
	@DisplayName("finish: 결과 저장")
	void finish_storesResult() {
		adapter.finish("baz", "42");

		verify(valueOps).set(
			eq("idempotency:res:baz"),
			eq("42"),
			eq(Duration.ofDays(15))
		);
	}

	@Test
	@DisplayName("getResult: 저장된 결과 반환")
	void getResult_returnsStored() {
		when(valueOps.get("idempotency:res:baz")).thenReturn("42");

		assertEquals("42", adapter.getResult("baz"));
	}

	@Test
	@DisplayName("invalidate: 모든 키 삭제")
	void invalidate_deletesBothKeys() {
		adapter.invalidate("qux");

		verify(redisTemplate).delete("idempotency:rec:qux");
		verify(redisTemplate).delete("idempotency:res:qux");
	}

	@Test
	@DisplayName("getOrCreateKey: 최초 생성 시 새로운 키 반환")
	void getOrCreateKey_newCreation() {
		String record = "rec1";
		// 처음 저장 시 setIfAbsent → true
		when(valueOps.setIfAbsent(
			eq("idempotency:rec:record:" + record),
			anyString(),
			eq(Duration.ofDays(15))
		)).thenReturn(true);

		String key1 = adapter.getOrCreateKey(record);
		assertNotNull(key1, "처음 생성된 키는 null이 아님");

		// 다시 새로 생성되면 또 다른 UUID
		when(valueOps.setIfAbsent(
			eq("idempotency:rec:record:" + record),
			anyString(),
			eq(Duration.ofDays(15))
		)).thenReturn(true);

		String key2 = adapter.getOrCreateKey(record);
		assertNotNull(key2);
		assertNotEquals(key1, key2, "두 번 생성된 키가 달라야 함");
	}

	@Test
	@DisplayName("getOrCreateKey: 이미 존재하는 키가 있으면 기존 키 반환")
	void getOrCreateKey_existing() {
		String record = "rec2";
		String existing = "uuid-1234";

		when(valueOps.setIfAbsent(
			eq("idempotency:rec:record:" + record),
			anyString(),
			eq(Duration.ofDays(15))
		)).thenReturn(false);

		when(valueOps.get(
			eq("idempotency:rec:record:" + record)
		)).thenReturn(existing);

		assertEquals(existing, adapter.getOrCreateKey(record));
	}

	@Test
	@DisplayName("getOrCreateKey: setIfAbsent=false 이고 get이 null 이면 fallback 키 반환")
	void getOrCreateKey_existingNullFallsback() {
		String record = "rec3";

		when(valueOps.setIfAbsent(
			eq("idempotency:rec:record:" + record),
			anyString(),
			eq(Duration.ofDays(15))
		)).thenReturn(false);

		when(valueOps.get(
			eq("idempotency:rec:record:" + record)
		)).thenReturn(null);

		String fallback = adapter.getOrCreateKey(record);
		assertNotNull(fallback, "get이 null인 경우에도 새 키를 반환해야 함");
	}
}