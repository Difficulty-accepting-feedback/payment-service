package com.grow.payment_service.payment.infra.client;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationRequest {
	@NotNull private String notificationType;
	@NotNull private Long memberId;
	@NotNull private String title;
	@NotNull private String content;
	private String orderId;
	private Long   amount;
	private LocalDateTime occurredAt;
}