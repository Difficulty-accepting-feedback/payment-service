package com.grow.payment_service.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
public class MicrometerAopConfig {
	@Bean
	TimedAspect timedAspect(MeterRegistry registry) { return new TimedAspect(registry); }

	@Bean
	CountedAspect countedAspect(MeterRegistry registry) { return new CountedAspect(registry); }

	@Bean
	MeterRegistryCustomizer<MeterRegistry> commonTags(
		@Value("${spring.application.name:payment-service}") String appName
	) {
		return registry -> registry.config().commonTags(
			"app", appName,
			"service", "payment"
		);
	}
}