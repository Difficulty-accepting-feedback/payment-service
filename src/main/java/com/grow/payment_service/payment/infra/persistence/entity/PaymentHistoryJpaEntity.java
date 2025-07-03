package com.grow.payment_service.payment.infra.persistence.entity;

import java.time.LocalDateTime;

import com.grow.payment_service.payment.domain.enums.PayStatus;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@Table(name = "payment_history")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class PaymentHistoryJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long paymentHistoryId;

	@Column(nullable = false)
	private Long paymentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PayStatus status;

	@Column(nullable = false)
	private LocalDateTime changedAt;

	@Column(columnDefinition = "TEXT")
	private String reasonDetail;
}