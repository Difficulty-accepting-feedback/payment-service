# MSA: payment service
### 자기 계발 및 취미 매칭 웹 애플리케이션, GROW 🌳

**GROW Payment Service**는 플랫폼 전체의 **결제 도메인**을 담당하는 핵심 마이크로서비스입니다.  
정기결제(자동결제)·일회성 결제·가상계좌·환불/취소·웹훅 검증까지 **엔드투엔드 결제 흐름**을 책임지며,  
**토스 페이먼츠(Toss Payments)** 외부 API와 연동하여 **안정적 결제 승인/보상 트랜잭션/멱등성**을 보장합니다.  
결제 이벤트는 멤버·알림 서비스와 Kafka로 연결되어 **포인트/업적/알림**에 반영됩니다.

---

## 👥 팀

|                                           장무영                                           |                                           최지선                                           |
|:---------------------------------------------------------------------------------------:|:---------------------------------------------------------------------------------------:|
| <img src="https://avatars.githubusercontent.com/u/136911104?v=4" alt="장무영" width="200"> | <img src="https://avatars.githubusercontent.com/u/192316487?v=4" alt="최지선" width="200"> | 
|                           [GitHub](https://github.com/wkdan)                            |                        [GitHub](https://github.com/wesawth3sun)                         |

---

## 🏗️ 아키텍처 개요

서비스는 **DDD(Domain-Driven Design)** 원칙을 중심으로 **Hexagonal Architecture (Ports & Adapters)** 구조로 설계되어,  
도메인 로직과 인프라 의존성을 명확히 분리하고 **높은 응집도·낮은 결합도**를 유지합니다.  
핵심 비즈니스 규칙은 `domain` 계층(Payment, PaymentHistory, PayStatus, CancelReason 등)에서 관리되며,  
외부 연동(예: **Toss Payments**, Kafka, Redis, JPA)은 `infra` 어댑터를 통해 주입됩니다.  
`application` 계층은 트랜잭션 단위 유스케이스(예: 결제 승인/취소, 자동결제 배치, 가상계좌 발급, 보상 트랜잭션)를 오케스트레이션하고,  
`presentation` 계층은 REST API(승인/취소/웹훅/가상계좌)로 **게이트웨이**와 통신합니다.

또한 **SAGA 오케스트레이션**과 **Retry/Compensation** 레이어(`PaymentSagaOrchestrator`,  
`RetryablePersistenceService`, `CompensationTransactionService`)를 적용해 외부 호출 실패에도 **일관성**을 확보합니다.

---

## 🧩 운영 구조

모든 마이크로서비스(member, notification, quiz 등)는 Kubernetes 환경에서 컨테이너 단위로 배포되며,  
게이트웨이와 데이터 계층, 외부 연동 API까지 완전한 클라우드 네이티브 구조로 설계되었습니다.

<img width="1541" height="1241" alt="image" src="https://github.com/user-attachments/assets/8a40b1c6-0bdb-4414-86b4-eee9f298d6ca" />

서비스 간 연결은 **Gateway, Kafka, Redis, Kubernetes**를 중심으로 구성되어 있습니다.

| 구성 요소 | 역할 |
|------------|------|
| 🧭 **Gateway (Spring Cloud Gateway)** | JWT 기반 인증 검증 및 요청 라우팅. 결제 승인/취소/웹훅 엔드포인트 프록시 |
| 🧵 **Kafka (Event Bus)** | 결제 승인/취소/자동결제 결과 이벤트 발행, 멤버/알림 서비스가 구독 |
| 💾 **Redis (Cache & Lock)** | **멱등 처리(Idempotency Key)**, 결제 키 중복 방지, 자동결제 작업 **분산락** |
| 🗃️ **MySQL (Primary DB)** | 결제/이력/주문/구독 상태의 영속 저장소 |
| ☸️ **Kubernetes** | 서비스 배포·스케일링·롤링 업데이트 자동화로 고가용성(HA) 확보 |
| ⏱️ **Quartz Scheduler** | **자동결제 배치** 및 재시도 Job 스케줄링 |
| 📊 **Prometheus + Grafana** | 승인/취소 성공률, 상태 전이, 보상 트랜잭션, p95 지연 등 메트릭 모니터링 |
| 💳 **Toss Payments(API)** | 결제 승인/취소, 빌링키(정기결제), 가상계좌 발급/입금 웹훅 처리 |

---

## 🧩 주요 기능 요약

| 구분 | 기능 설명 |
|------|------------|
| 💳 **일회성 결제 승인/취소** | 토스 결제 승인/취소 API 연동, 상태 전이(`PayStatus`)와 이력 기록 |
| 🔁 **정기결제(자동결제)** | 빌링키 발급/보관/삭제, 월/일 단위 **Quartz 배치**로 자동 승인 및 실패 처리 |
| 🏦 **가상계좌 발급/입금 처리** | 가상계좌 생성, **입금 웹훅** 수신 후 결제 확정/영수증 알림 |
| 🧷 **멱등성/분산락** | Redis 기반 **Idempotency Adapter**로 중복 승인/취소 방지, 락으로 동시 처리 제어 |
| 🧩 **보상 트랜잭션(SAGA)** | 외부 승인 실패/부분 실패 시 **Compensation**으로 데이터 일관성 유지 |
| 🔐 **웹훅 검증** | 시그니처 검증/재생 공격 방지/멱등 처리로 안전한 웹훅 소비 |
| 📈 **메트릭/알람** | 승인률/취소률, 상태전이 카운트, 예외 Top, **compensation_total** 추적 |
| 📨 **도메인 연동** | 결제 결과를 Kafka로 발행 → **Member/Notification** 서비스에서 포인트·알림 반영 |

---

## 🛠️ 기술 스택

### FrontEnd
<div> 
  <img src="https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white"/>
  <img src="https://img.shields.io/badge/Next.js-000000?style=for-the-badge&logo=next.js&logoColor=white"/>
</div>

### BackEnd
<div> 
  <img src="https://img.shields.io/badge/Java-007396?style=for-the-badge&logo=java&logoColor=white"/>
  <img src="https://img.shields.io/badge/SpringBoot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white"/>
  <img src="https://img.shields.io/badge/Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=black"/>
</div>

### Database
<div> 
  <img src="https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white"/>
  <img src="https://img.shields.io/badge/redis-%23DD0031.svg?style=for-the-badge&logo=redis&logoColor=white"/>
  <img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white"/>
</div>

### IDLE&Tool
<div> 
  <img src="https://img.shields.io/badge/IntelliJ%20IDEA-000000?style=for-the-badge&logo=intellijidea&logoColor=white"/>
  <img src="https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white"/>
</div>

### OPEN API
<div>
  <img src="https://img.shields.io/badge/Toss%20Payments-0064FF?style=for-the-badge&logo=toss&logoColor=white"/>
</div>

### Event Bus / Messaging
<div>
  <img src="https://img.shields.io/badge/Apache%20Kafka-231F20?style=for-the-badge&logo=apachekafka&logoColor=white"/>
</div>

### Infra
<div>
  <img src="https://img.shields.io/badge/Linux-FCC624?style=for-the-badge&logo=linux&logoColor=black"/>
  <img src="https://img.shields.io/badge/AWS-232F3E?style=for-the-badge&logo=amazonwebservices&logoColor=white"/>
  <img src="https://img.shields.io/badge/Vercel-000000?style=for-the-badge&logo=vercel&logoColor=white"/>
</div>

### Container & Orchestration
<div>
  <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white"/>
  <img src="https://img.shields.io/badge/Kubernetes-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white"/>
</div>

### Monitoring
<div>
  <img src="https://img.shields.io/badge/Prometheus-E6522C?style=for-the-badge&logo=prometheus&logoColor=white"/>
  <img src="https://img.shields.io/badge/Grafana-F46800?style=for-the-badge&logo=grafana&logoColor=white"/>
</div>

### CI/CD
<div>
  <img src="https://img.shields.io/badge/GitHub%20Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white"/>
  <img src="https://img.shields.io/badge/ArgoCD-EF7B4D?style=for-the-badge&logo=argo&logoColor=white"/>
</div>

---

## Conventional Commits 규칙

| 이모지 | 타입      | 설명                                               | 예시 커밋 메시지                                   |
|--------|-----------|--------------------------------------------------|--------------------------------------------------|
| ✨     | feat      | 새로운 기능 추가                                    | feat: 로그인 기능 추가                             |
| 🐛     | fix       | 버그 수정                                          | fix: 회원가입 시 이메일 중복 체크 오류 수정         |
| 📝     | docs      | 문서 수정                                          | docs: README 오타 수정                            |
| 💄     | style     | 코드 포맷, 세미콜론 누락 등 스타일 변경 (기능 변경 없음) | style: 코드 정렬 및 세미콜론 추가                  |
| ♻️     | refactor  | 코드 리팩토링 (기능 변경 없음)                     | refactor: 중복 코드 함수로 분리                    |
| ⚡     | perf      | 성능 개선                                          | perf: 이미지 로딩 속도 개선                       |
| ✅     | test      | 테스트 코드 추가/수정                              | test: 유저 API 테스트 코드 추가                    |
| 🛠️     | build     | 빌드 시스템 관련 변경                              | build: 배포 스크립트 수정                         |
| 🔧     | ci        | CI 설정 변경                                      | ci: GitHub Actions 워크플로우 수정                |

```text
타입(범위): 간결한 설명 (50자 이내, 한글 작성)

(필요시) 변경 이유/상세 내용
```
- 하나의 커밋에는 하나의 목적만 담기

    → 여러 변경 사항을 한 커밋에 몰아넣지 않기
- 제목 끝에 마침표(.)를 붙이지 않기
- 본문(Body)은 선택 사항이지만, 변경 이유나 상세 설명이 필요할 때 작성

    → 72자 단위로 줄바꿈, 제목과 본문 사이에 한 줄 띄우기
- 작업 중간 저장은 WIP(Work In Progress)로 표시할 것

  → 예) WIP: 회원가입 로직 구현 중

---
## 🕒 협업 시간 안내

팀원들이 주로 활동하는 시간대입니다.  
이 시간에 맞춰 커뮤니케이션과 코드 리뷰, 회의 등을 진행합니다.

| 요일     | 활동 시간                  |
|----------|----------------------------|
| 📅 평일  | 14:00 ~ 18:00, 20:00 ~ 23:00 |
| 📅 주말  | 14:00 ~ 18:00               |

---

## 🧐 코드 리뷰 규칙

- PR 제목과 설명을 명확하게 작성 (변경 내용, 목적, 참고 이슈 등 포함)
- Conventional Commits 규칙을 준수하여 커밋 메시지 작성
- 하나의 PR에는 하나의 기능/이슈만 포함
- 코드 스타일, 네이밍, 로직, 성능, 보안, 예외 처리 등 꼼꼼히 확인
- 리뷰 코멘트에는 반드시 답변, 필요시 추가 커밋으로 반영
- 모든 리뷰 코멘트 resolve 후 머지
- 스쿼시 머지 방식 권장, 충돌 발생 시 머지 전 해결
- 리뷰는 24시간 이내 진행, 모르는 부분은 적극적으로 질문
- 리뷰 과정에서 배운 점은 팀 문서에 공유 (트러블 슈팅 등)
