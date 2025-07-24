package com.grow.payment_service.payment.infra.paymentprovider;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TossBillingAuthResponse {
	private String mId;
	private String customerKey;
	private String authenticatedAt;
	private String method;
	private String billingKey;
	private CardInfo card;
	private String cardCompany;
	private String cardNumber;


	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CardInfo {
		/** 카드 발급사 코드*/
		private String issuerCode;
		/** 카드 매입사 코드*/
		private String acquirerCode;
		/** 카드 번호 일부 마스킹 (예: "12345678****123*") */
		private String number;
		/** 카드 종류 (신용, 체크, 기프트) */
		private String cardType;
		/** 소유자 타입 (개인, 법인) */
		private String ownerType;
		/** 할부개월 수 */
		private Integer installmentPlanMonths;
		/** 무이자 여부 */
		private Boolean isInterestFree;
		/** 이자 부담 주체 */
		private String interestPayer;
		/** 승인 번호 */
		private String approveNo;
		/** 카드포인트 사용 여부 */
		private Boolean useCardPoint;
		/** 매입 상태 (예: "READY") */
		private String acquireStatus;
	}
}