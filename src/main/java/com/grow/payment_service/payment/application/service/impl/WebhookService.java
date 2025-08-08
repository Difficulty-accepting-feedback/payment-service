package com.grow.payment_service.payment.application.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grow.payment_service.global.dto.RsData;
import com.grow.payment_service.payment.application.service.EmailService;
import com.grow.payment_service.payment.infra.client.MemberClient;
import com.grow.payment_service.payment.infra.client.MemberInfoResponse;
import com.grow.payment_service.payment.presentation.dto.WebhookRequest;
import com.grow.payment_service.payment.domain.model.Payment;
import com.grow.payment_service.payment.domain.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

	private final PaymentRepository paymentRepository;
	private final MemberClient memberClient;
	private final EmailService emailService;

	/**
	 * 결제 완료/실패 웹훅 처리
	 */
	@Transactional
	public void onPaymentStatusChanged(WebhookRequest.WebhookData d) {
		// 1) 완료 상태가 아니면 무시
		if (!"DONE".equals(d.getStatus()) && !"FAILED".equals(d.getStatus())) {
			return;
		}

		// 2) 주문 정보 조회
		Payment payment = paymentRepository.findByOrderId(d.getOrderId())
			.orElseThrow(() -> new IllegalArgumentException(
				"Unknown orderId for webhook: " + d.getOrderId()
			));

		Long memberId = payment.getMemberId();

		// 3) 멤버 서비스에서 실제 이메일·이름 조회
		RsData<MemberInfoResponse> rs = memberClient.getMyInfo(memberId);
		MemberInfoResponse profile = rs.getData();
		String email = profile.getEmail();
		String name  = profile.getNickname();

		// 4) 이메일이 없으면 로깅만
		if (email == null || email.isBlank()) {
			log.warn("[웹훅 이메일 미전달] memberId={} orderId={}", memberId, d.getOrderId());
			return;
		}

		// 5) 성공/실패에 따라 발송
		if ("DONE".equals(d.getStatus())) {
			emailService.sendPaymentSuccess(
				email,
				name,
				d.getOrderId(),
				d.getAmount()
			);
		} else {
			emailService.sendPaymentFailure(
				email,
				name,
				d.getOrderId(),
				d.getAmount()
			);
		}
	}

	/**
	 * 결제 취소 완료 웹훅 처리
	 */
	@Transactional
	public void onCancelStatusChanged(WebhookRequest.WebhookData d) {
		// 1) CANCELLED 상태가 아니면 무시
		if (!"CANCELLED".equals(d.getStatus())) {
			return;
		}

		// 2) 주문 정보 조회
		Payment payment = paymentRepository.findByOrderId(d.getOrderId())
			.orElseThrow(() -> new IllegalArgumentException(
				"Unknown orderId for webhook: " + d.getOrderId()
			));

		Long memberId = payment.getMemberId();

		// 3) 멤버 서비스에서 이메일·이름 조회
		RsData<MemberInfoResponse> rs = memberClient.getMyInfo(memberId);
		MemberInfoResponse profile = rs.getData();
		String email = profile.getEmail();
		String name  = profile.getNickname();

		if (email == null || email.isBlank()) {
			log.warn("[웹훅 이메일 미전달] memberId={} orderId={}", memberId, d.getOrderId());
			return;
		}

		// 4) 취소 안내 메일 발송
		emailService.sendCancellation(
			email,
			name,
			d.getOrderId(),
			d.getAmount()
		);
	}
}