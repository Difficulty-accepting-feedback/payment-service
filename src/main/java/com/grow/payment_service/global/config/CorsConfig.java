package com.grow.payment_service.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

	@Bean
	public WebMvcConfigurer corsConfigurer() {
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				registry.addMapping("/api/**")
					.allowedOrigins("http://localhost:3000")   // 프론트 도메인
					.allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS")
					.allowedHeaders(
						"Content-Type","Accept","Authorization","X-Requested-With",
						"X-Authorization-Id","Idempotency-Key","memberId"
					)
					.allowCredentials(true)
					.maxAge(3600);
			}
		};
	}
}