package com.grow.payment_service.payment.infra.persistence.entity;

import com.grow.payment_service.payment.domain.model.enums.CancelReason;
import com.grow.payment_service.payment.domain.model.enums.FailureReason;
import com.grow.payment_service.payment.domain.model.enums.PayStatus;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@Table(name = "payment")
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PaymentJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long paymentId;

	@Column(nullable = false)
	private Long memberId;

	@Column(nullable = false)
	private Long planId;

	@Column(nullable = false, unique = true)
	private Long orderId;

	@Column(unique = true)
	private String paymentKey;

	private String billingKey;

	private String customerKey;

	@Column(nullable = false)
	private Long totalAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PayStatus payStatus;

	@Column(nullable = false)
	private String method;

	@Enumerated(EnumType.STRING)
	private FailureReason failureReason;

	@Enumerated(EnumType.STRING)
	private CancelReason cancelReason;
}