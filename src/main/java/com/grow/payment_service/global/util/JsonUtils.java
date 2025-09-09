package com.grow.payment_service.global.util;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Component
public class JsonUtils {
	private static final ObjectMapper objectMapper = new ObjectMapper()
		.registerModule(new JavaTimeModule())                 // LocalDateTime 지원
		.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // ISO-8601 문자열로 직렬화

	private JsonUtils() {
	}

	/**
	 * 주어진 객체를 JSON 문자열로 변환합니다.
	 *
	 * 이 메서드는 Jackson의 ObjectMapper를 사용하여 객체를 직렬화합니다.
	 * 변환 중 오류가 발생하면 RuntimeException을 던집니다.
	 *
	 * @param <T> JSON으로 변환할 객체. null이 허용되며, null은 "null" 문자열로 변환됩니다.
	 * @return 객체를 나타내는 JSON 문자열.
	 * @throws RuntimeException JSON 직렬화에 실패할 경우 발생합니다. 내부적으로 JsonProcessingException을 래핑합니다.
	 */
	public static <T> String toJsonString(T object) {
		try {
			return objectMapper.writeValueAsString(object);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("JSON 직렬화 실패", e);
		}
	}

	public static <T> T fromJson(String json, Class<T> type) {
		try {
			return objectMapper.readValue(json, type);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("JSON 역직렬화 실패", e);
		}
	}
}