package com.grow.payment_service.global.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

	@Bean
	public NewTopic notificationRequested() {
		return TopicBuilder.name("payment.notification.requested")
			.partitions(3)
			.replicas(3)
			.build();
	}
}