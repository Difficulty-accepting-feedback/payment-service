package com.grow.payment_service.global.metrics;

import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class PaymentMetrics {

	private final MeterRegistry registry;

	// 상태 전이 카운터
	public void transition(String from, String to) {
		registry.counter("payment_state_transition_total", "from", from, "to", to).increment();
	}

	// 결과 카운터
	public void result(String name, String... tags) {
		registry.counter(name, tags).increment();
	}

	// null/blank 라벨 방지
	public static String v(String s) { return (s == null || s.isBlank()) ? "unknown" : s; }
}