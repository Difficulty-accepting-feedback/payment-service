package com.grow.payment_service.payment.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.grow.payment_service.global.dto.RsData;
import com.grow.payment_service.payment.infra.client.MemberClient;
import com.grow.payment_service.payment.infra.client.MemberInfoResponse;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

@Hidden
@RestController
@RequestMapping("/api/payments/test")
@RequiredArgsConstructor
@Tag(name = "Test", description = "테스트용 API")
public class TestMemberController {

	private final MemberClient memberClient;

	@Operation(summary = "테스트: 헤더의 회원 ID로 멤버 정보 조회")
	@GetMapping("/member-info")
	public ResponseEntity<RsData<MemberInfoResponse>> getMemberInfo(
		@Parameter(description = "요청자 회원 ID")
		@RequestHeader("X-Authorization-Id") Long memberId
	) {
		RsData<MemberInfoResponse> resp = memberClient.getMyInfo(memberId);
		return ResponseEntity.ok(new RsData<>("200", "테스트용 멤버 정보 조회 성공", resp.getData()));
	}
}