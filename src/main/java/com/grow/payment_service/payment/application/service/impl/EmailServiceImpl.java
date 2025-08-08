package com.grow.payment_service.payment.application.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.grow.payment_service.payment.application.service.EmailService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

	private final JavaMailSender mailSender;

	@Value("${app.mail.from}")
	private String fromAddress;

	@Override
	public void sendPaymentSuccess(String toEmail, String toName, String orderId, Integer amount) {
		SimpleMailMessage msg = new SimpleMailMessage();
		msg.setFrom(fromAddress);
		msg.setTo(toEmail);
		msg.setSubject("[GROW] 결제 성공 안내");
		msg.setText(String.format(
			"%s님, 주문번호 %s번의 %d원 결제가 성공적으로 완료되었습니다.%n감사합니다!",
			toName, orderId, amount
		));
		mailSender.send(msg);
	}

	@Override
	public void sendPaymentFailure(String toEmail, String toName, String orderId, Integer amount) {
		SimpleMailMessage msg = new SimpleMailMessage();
		msg.setFrom(fromAddress);
		msg.setTo(toEmail);
		msg.setSubject("[GROW] 결제 실패 안내");
		msg.setText(String.format(
			"%s님, 주문번호 %s번의 %d원 결제가 실패하였습니다.%n결제 정보를 확인해주세요.",
			toName, orderId, amount
		));
		mailSender.send(msg);
	}

	@Override
	public void sendCancellation(String toEmail, String toName, String orderId, Integer amount) {
		SimpleMailMessage msg = new SimpleMailMessage();
		msg.setFrom(fromAddress);
		msg.setTo(toEmail);
		msg.setSubject("[GROW] 결제 취소 안내");
		msg.setText(String.format(
			"%s님, 주문번호 %s번의 %d원 결제가 취소되었습니다.%n환불 절차가 진행 중입니다.",
			toName, orderId, amount
		));
		mailSender.send(msg);
	}
}