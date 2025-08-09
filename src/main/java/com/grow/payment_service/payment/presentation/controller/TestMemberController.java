package com.grow.payment_service.payment.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grow.payment_service.global.dto.RsData;
import com.grow.payment_service.payment.infra.client.MemberClient;
import com.grow.payment_service.payment.infra.client.MemberInfoResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/payments/test")
@RequiredArgsConstructor
public class TestMemberController {

	private final MemberClient memberClient;

	/**
	 * 테스트용: 헤더의 X-Authorization-Id 로 멤버 정보 조회
	 */
	@GetMapping("/member-info")
	public ResponseEntity<RsData<MemberInfoResponse>> getMemberInfo(
		@RequestHeader("X-Authorization-Id") Long memberId
	) {
		// 1) 페이먼트 → 멤버 서비스 Feign 호출
		RsData<MemberInfoResponse> resp = memberClient.getMyInfo(memberId);
		// 2) 그대로 클라이언트에 리턴
		return ResponseEntity.ok(
			new RsData<>("200", "테스트용 멤버 정보 조회 성공", resp.getData())
		);
	}
}