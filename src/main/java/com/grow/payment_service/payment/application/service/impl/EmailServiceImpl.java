package com.grow.payment_service.payment.application.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.grow.payment_service.global.metrics.PaymentMetrics;
import com.grow.payment_service.payment.application.service.EmailService;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

	private final JavaMailSender mailSender;
	private final PaymentMetrics metrics;

	@Value("${app.mail.from}")
	private String fromAddress;

	@Override
	public void sendPaymentSuccess(
		String toEmail,
		String toName,
		String orderId,
		Integer amount,
		String receiptUrl,
		String method,
		String easyPayProvider,
		String requestedAt,
		String approvedAt,
		String currency
	) {
		try {
			sendStyledEmail(
				toEmail, toName,
				"결제 완료 안내",
				toName + "님, 결제가 완료되었어요.",
				builder -> {
					builder.appendRow("구매상품", "GROW Plan #" + orderId);
					builder.appendRow("결제금액", String.format("%,d원", amount));
					builder.appendRow("결제수단", PaymentMetrics.v(easyPayProvider) + " - " + PaymentMetrics.v(method));
					builder.appendRow("결제일시", approvedAt.replace('T',' '));
					builder.appendRow("주문번호", orderId);
				},
				receiptUrl
			);
			metrics.result("payment_email_send_total","type","success","result","success");
		} catch (Exception e) {
			metrics.result("payment_email_send_total","type","success","result","error","exception",e.getClass().getSimpleName());
			throw e;
		}
	}


	@Override
	public void sendPaymentFailure(
		String toEmail,
		String toName,
		String orderId,
		Integer amount,
		String receiptUrl,
		String method,
		String easyPayProvider,
		String requestedAt,
		String approvedAt,
		String currency
	) {
		try {
			sendStyledEmail(
				toEmail, toName,
				"결제 실패 안내",
				toName + "님, 결제가 실패했습니다.",
				builder -> {
					builder.appendRow("구매상품", "GROW Plan #" + orderId);
					builder.appendRow("시도 금액", String.format("%,d원", amount));
					builder.appendRow("결제수단", PaymentMetrics.v(easyPayProvider) + " - " + PaymentMetrics.v(method));
					builder.appendRow("결제일시", PaymentMetrics.v(approvedAt).replace('T',' '));
					builder.appendRow("주문번호", orderId);
				},
				receiptUrl
			);
			metrics.result("payment_email_send_total","type","failure","result","success");
		} catch (Exception e) {
			metrics.result("payment_email_send_total","type","failure","result","error","exception",e.getClass().getSimpleName());
			throw e;
		}
	}

	@Override
	public void sendCancellation(
		String toEmail,
		String toName,
		String orderId,
		Integer amount,
		String receiptUrl,
		String method,
		String easyPayProvider,
		String requestedAt,
		String approvedAt,
		String cancelReason,
		Integer cancelAmount,
		String currency
	) {
		try {
			sendStyledEmail(
				toEmail, toName,
				"결제 취소 안내",
				toName + "님, 주문이 취소되었어요.",
				builder -> {
					builder.appendRow("구매상품", "GROW Plan #" + orderId);
					builder.appendRow("취소 금액", String.format("%,d원", cancelAmount));
					builder.appendRow("취소 사유", PaymentMetrics.v(cancelReason));
					builder.appendRow("결제수단", PaymentMetrics.v(easyPayProvider) + " - " + PaymentMetrics.v(method));
					builder.appendRow("취소일시", PaymentMetrics.v(approvedAt).replace('T',' '));
					builder.appendRow("주문번호", orderId);
				},
				receiptUrl
			);
			metrics.result("payment_email_send_total","type","cancellation","result","success");
		} catch (Exception e) {
			metrics.result("payment_email_send_total","type","cancellation","result","error","exception",e.getClass().getSimpleName());
			throw e;
		}
	}

	// 공통 이메일 생성 로직
	private void sendStyledEmail(
		String toEmail,
		String toName,
		String subject,
		String title,
		EmailContentBuilder.Consumer contentFiller,
		String actionUrl
	) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
			helper.setFrom(fromAddress);
			helper.setTo(toEmail);
			helper.setSubject(subject);

			EmailContentBuilder builder = new EmailContentBuilder();
			builder.begin()
				.header(title)
				.openTable();
			contentFiller.accept(builder);
			builder.closeTable();
			if (actionUrl != null) {
				builder.actionButton(actionUrl, "매출전표 보기");
			}
			builder.footer();

			helper.setText(builder.build(), true);
			mailSender.send(message);
			log.info("{} 메일 전송 완료 → orderId={}, email={}", subject, toName, toEmail);
		} catch (Exception e) {
			log.error(subject + " 이메일 전송 실패", e);
		}
	}

	// 내부 DSL: 이메일 콘텐츠를 쉽게 구성하는 빌더
	private static class EmailContentBuilder {
		private final StringBuilder sb = new StringBuilder();

		interface Consumer { void accept(EmailContentBuilder b); }

		EmailContentBuilder begin() {
			sb.append("<div style=\"font-family:Apple SD Gothic Neo, sans-serif; padding:20px;\">")
				.append("<h1 style=\"margin:0; font-size:28px; color:#333;\">GROW</h1>");
			return this;
		}

		EmailContentBuilder header(String text) {
			sb.append("<h2 style=\"margin:20px 0 10px; font-size:24px;\">")
				.append(text)
				.append("</h2>");
			return this;
		}

		EmailContentBuilder openTable() {
			sb.append("<table style=\"width:100%; border-collapse:collapse;\">");
			return this;
		}

		EmailContentBuilder appendRow(String label, String value) {
			sb.append("<tr style=\"border-bottom:1px solid #eee;\">")
				.append("<td style=\"padding:8px; color:#555; width:30%;\">")
				.append(label).append("</td>")
				.append("<td style=\"padding:8px; color:#333;\">")
				.append(value).append("</td>")
				.append("</tr>");
			return this;
		}

		EmailContentBuilder closeTable() {
			sb.append("</table>");
			return this;
		}

		EmailContentBuilder actionButton(String url, String text) {
			sb.append("<p style=\"margin:20px 0;\"><a href=\"")
				.append(url)
				.append("\" style=\"display:inline-block; padding:10px 20px; background:#346beb; color:#fff; text-decoration:none; border-radius:4px;\">")
				.append(text)
				.append("</a></p>");
			return this;
		}

		EmailContentBuilder footer() {
			sb.append("<hr style=\"border:none; border-top:1px solid #eee; margin:20px 0;\"/>")
				.append("<p style=\"font-size:12px; color:#888; line-height:1.5;\">")
				.append("본 메일은 GROW 시스템에서 자동 발송되었습니다.<br/>")
				.append("문의: support@grow.com")
				.append("</p>")
				.append("</div>");
			return this;
		}

		String build() {
			return sb.toString();
		}
	}
}