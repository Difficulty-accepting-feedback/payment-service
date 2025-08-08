package com.grow.payment_service.payment.application.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.grow.payment_service.global.dto.RsData;
import com.grow.payment_service.payment.application.service.EmailService;
import com.grow.payment_service.payment.infra.client.MemberClient;
import com.grow.payment_service.payment.infra.client.MemberInfoResponse;
import com.grow.payment_service.payment.presentation.dto.WebhookRequest;
import com.grow.payment_service.payment.presentation.dto.WebhookRequest.WebhookData.Cancel;
import com.grow.payment_service.payment.presentation.dto.WebhookRequest.WebhookData.EasyPay;
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
		log.info("[웹훅 처리 시작] orderId={}, status={}", d.getOrderId(), d.getStatus());

		// 2) 주문 정보 조회
		log.info("[1/4] 주문 정보 조회 중... orderId={}", d.getOrderId());
		Payment payment = paymentRepository.findByOrderId(d.getOrderId())
			.orElseThrow(() -> new IllegalArgumentException(
				"Unknown orderId for webhook: " + d.getOrderId()
			));
		Long memberId = payment.getMemberId();
		log.info("[1/4] 주문 정보 조회 완료 → paymentId={}, memberId={}",
			payment.getPaymentId(), memberId);

		// 3) 멤버 서비스에서 실제 이메일·이름 조회
		log.info("[2/4] 멤버 서비스 호출 중... memberId={}", memberId);
		RsData<MemberInfoResponse> rs = memberClient.getMyInfo(memberId);
		MemberInfoResponse profile = rs.getData();
		String email = profile.getEmail();
		String name = profile.getNickname();
		log.info("[2/4] 멤버 정보 조회 완료 → email={}, nickname={}", email, name);

		// 4) 이메일이 없으면 로깅만
		if (email == null || email.isBlank()) {
			log.warn("[웹훅 이메일 미전달] memberId={} orderId={}", memberId, d.getOrderId());
			return;
		}

		// 5) 영수증 및 추가 결제 정보 추출
		log.info("[3/4] 영수증 및 결제 정보 추출 중...");
		String receiptUrl = d.getReceipt() != null ? d.getReceipt().getUrl() : null;
		String method = d.getMethod();
		EasyPay easyPay = d.getEasyPay();
		String provider = easyPay != null ? easyPay.getProvider() : null;
		String requestedAt = d.getRequestedAt();
		String approvedAt = d.getApprovedAt();
		String currency = d.getCurrency();
		log.info("[3/4] 추출 완료 → receiptUrl={}, method={}, provider={}, requestedAt={}, approvedAt={}, currency={}",
			receiptUrl, method, provider, requestedAt, approvedAt, currency);

		// 6) 성공/실패에 따라 발송
		log.info("[4/4] 이메일 발송 호출 → type={}, orderId={}, email={}",
			d.getStatus(), d.getOrderId(), email);
		if ("DONE".equals(d.getStatus())) {
			emailService.sendPaymentSuccess(
				email,
				name,
				d.getOrderId(),
				d.getAmount(),
				receiptUrl,     // 영수증 URL
				method,         // 결제 수단
				provider,       // provider
				requestedAt,    // 요청 시각
				approvedAt,     // 승인 시각
				currency        // 통화
			);
		} else {
			emailService.sendPaymentFailure(
				email,
				name,
				d.getOrderId(),
				d.getAmount(),
				receiptUrl,
				method,
				provider,
				requestedAt,
				approvedAt,
				currency
			);
		}
		log.info("[4/4] 이메일 발송 완료 → orderId={}, email={}", d.getOrderId(), email);
	}

	/**
	 * 결제 취소 완료 웹훅 처리
	 */
	@Transactional
	public void onCancelStatusChanged(WebhookRequest.WebhookData d) {
		// 1) CANCELED 상태가 아니면 무시
		if (!"CANCELED".equals(d.getStatus())) {
			return;
		}
		log.info("[웹훅 처리 시작 - 취소] orderId={}, status={}", d.getOrderId(), d.getStatus());

		// 2) 주문 정보 조회
		log.info("[1/4] 주문 정보 조회 중... orderId={}", d.getOrderId());
		Payment payment = paymentRepository.findByOrderId(d.getOrderId())
			.orElseThrow(() -> new IllegalArgumentException(
				"Unknown orderId for webhook: " + d.getOrderId()
			));
		Long memberId = payment.getMemberId();
		log.info("[1/4] 주문 정보 조회 완료 → paymentId={}, memberId={}",
			payment.getPaymentId(), memberId);

		// 3) 멤버 서비스 호출
		log.info("[2/4] 멤버 서비스 호출 중... memberId={}", memberId);
		RsData<MemberInfoResponse> rs = memberClient.getMyInfo(memberId);
		MemberInfoResponse profile = rs.getData();
		String email = profile.getEmail();
		String name = profile.getNickname();
		log.info("[2/4] 멤버 정보 조회 완료 → email={}, nickname={}", email, name);

		// 이메일 없으면 로깅 후 종료
		if (email == null || email.isBlank()) {
			log.warn("[웹훅 이메일 미전달] memberId={} orderId={}", memberId, d.getOrderId());
			return;
		}

		// 4) 영수증 및 취소 상세 정보 추출
		log.info("[3/4] 영수증 및 취소 정보 추출 중...");
		String receiptUrl = d.getReceipt() != null ? d.getReceipt().getUrl() : null;
		String method = d.getMethod();
		EasyPay easyPay = d.getEasyPay();
		String provider = easyPay != null ? easyPay.getProvider() : null;
		String requestedAt = d.getRequestedAt();
		String approvedAt = d.getApprovedAt();
		String currency = d.getCurrency();
		List<Cancel> cancels = d.getCancels();
		String cancelReason = (cancels != null && !cancels.isEmpty())
			? cancels.get(0).getCancelReason() : null;
		Integer cancelAmount = (cancels != null && !cancels.isEmpty())
			? cancels.get(0).getCancelAmount() : null;
		log.info(
			"[3/4] 추출 완료 → receiptUrl={}, method={}, provider={}, requestedAt={}, approvedAt={}, currency={}, cancelReason={}, cancelAmount={}",
			receiptUrl, method, provider, requestedAt, approvedAt, currency, cancelReason, cancelAmount);

		// 5) 취소 안내 메일 발송
		log.info("[4/4] 취소 안내 메일 발송 호출 → orderId={}, email={}", d.getOrderId(), email);
		emailService.sendCancellation(
			email,
			name,
			d.getOrderId(),
			d.getAmount(),
			receiptUrl,
			method,
			provider,
			requestedAt,
			approvedAt,
			cancelReason,
			cancelAmount,
			currency
		);
		log.info("[4/4] 취소 안내 메일 발송 완료 → orderId={}, email={}", d.getOrderId(), email);
	}
}