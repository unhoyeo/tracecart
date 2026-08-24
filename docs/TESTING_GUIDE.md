# TraceCart 테스트 전략

테스트는 모두 같은 방식으로 작성하지 않고, 실패 원인을 빠르게 찾을 수 있도록 계층을 나눴습니다.

```text
빠르고 격리된 단위 테스트
        ↓
Spring + H2 통합 테스트
        ↓
프로파일 빈 선택 테스트
        ↓
prod 전체 컨텍스트 기동 테스트
        ↓
Mock HTTP 기반 운영 결제 계약 테스트
```

## 1. 순수 단위 테스트

Spring 컨테이너, 데이터베이스, HTTP 서버를 사용하지 않습니다.

| 클래스 | 검증 내용 |
|---|---|
| `ProductTest` | 재고 차감·부족·복원과 0/음수 수량, 잘못된 생성 값 |
| `PurchaseOrderTest` | 상태 전이와 빈 사용자·잘못된 상품/수량/금액 차단 |
| `FakePaymentClientTest` | SUCCESS, FAILURE, TIMEOUT, 스레드 인터럽트 복원 |
| `OrderServiceUnitTest` | 성공·거절·타임아웃, 결제 명령, 404, 재고 부족, 서비스 직접 호출의 잘못된 입력 |
| `MdcTaskDecoratorTest` | 요청 MDC 복사와 실행기 MDC 복원 |

OrderService 단위 테스트는 Repository, PaymentClient, ApplicationEventPublisher를 Mockito mock으로 교체합니다. 따라서 실패하면 대부분 서비스 분기 자체의 문제입니다.

## 2. 통합 테스트

`@SpringBootTest`와 test 프로파일의 H2를 사용해 실제 Spring 빈, 트랜잭션, Hibernate를 함께 검증합니다.

| 클래스 | 검증 내용 |
|---|---|
| `OrderServiceIntegrationTest` | Fake 빈 선택, 성공·실패·타임아웃의 DB 상태와 재고 |
| `OrderApiIntegrationTest` | HTTP 201/400/404/409, traceId, 성공·실패·타임아웃, 경계값·깨진 JSON |
| `AsyncMdcIntegrationTest` | 실제 applicationTaskExecutor 스레드로 MDC 전달 |
| `OrderPaidAfterCommitIntegrationTest` | 커밋 후 알림 실행과 롤백 시 미실행 |
| `ProductOptimisticLockIntegrationTest` | 동시 재고 수정 중 하나만 커밋되는지 검증 |
| `RestClientAutoConfigurationTest` | Boot가 RestClient.Builder 빈을 생성하는지 확인 |

Fake 타임아웃은 업무 분기는 그대로 유지하지만 test 프로파일에서 지연 시간을 0ms로 덮어써 테스트를 빠르게 실행합니다.

## 3. 프로파일 테스트

`PaymentClientProfileTest`는 `ApplicationContextRunner`로 필요한 빈만 생성합니다.

```text
local → FakePaymentClient
dev   → FakePaymentClient
test  → FakePaymentClient
prod  → ExternalPaymentClient
```

전체 애플리케이션을 네 번 띄우지 않기 때문에 빠르면서 `@Profile` 표현식의 오류를 잡을 수 있습니다.

## 4. 운영환경은 어떻게 테스트하는가

자동 테스트가 실제 운영 데이터베이스나 실제 결제 서버에 연결해서는 안 됩니다. 대신 운영 코드와 설정을 유지하고 외부 인프라만 안전한 대역으로 교체합니다.

### 운영 프로파일 전체 조립

`ProdProfileContextTest`는 실제로 `prod` 프로파일을 활성화합니다.

```java
@ActiveProfiles("prod")
```

그러나 운영 비밀정보와 PostgreSQL 대신 테스트 프로퍼티와 H2를 높은 우선순위로 주입합니다.

```text
검증하는 것
- application-prod.yml 로딩
- prod Logback JSON 설정 파싱
- ExternalPaymentClient 단독 선택
- FakePaymentClient 미등록
- RestClient.Builder 자동 설정
- 연결 2초·읽기 3초 제한 시간의 실제 프로퍼티 바인딩
- JPA와 전체 Spring 컨텍스트 기동

검증하지 못하는 것
- PostgreSQL 고유 SQL과 데이터 타입 차이
- 실제 네트워크, DNS, TLS 인증서
- 실제 결제사의 요청 계약
- 운영 Secret과 방화벽 설정
```

### 운영 외부 결제 클라이언트

`ExternalPaymentClientTest`는 `MockRestServiceServer`를 RestClient.Builder에 연결합니다. 네트워크를 사용하지 않지만 실제 HTTP 요청 생성과 응답 처리는 그대로 실행합니다.

검증 시나리오:

1. POST `/payments` 성공과 거래 ID 역직렬화
2. 운영 요청 JSON의 orderId, userId, amount
3. HTTP 500을 PaymentException으로 변환
4. 네트워크 타임아웃을 PaymentException으로 변환
5. 2xx 빈 본문, 거래 ID 누락·공백을 실패로 처리
6. 깨진 성공 JSON을 PaymentException으로 변환
7. Fake 전용 PaymentScenario를 운영 요청에서 제외

### 실제 PostgreSQL 차이까지 검증하려면

CI에서는 별도 단계로 일회용 PostgreSQL 컨테이너를 띄우는 Testcontainers 테스트를 추가하는 것이 좋습니다. 이 단계에서는 운영 DB에 연결하지 않고 테스트가 끝나면 컨테이너를 폐기합니다.

```text
단위/통합 테스트
  → 매 커밋 실행

PostgreSQL Testcontainers
  → PR 또는 CI 실행

결제사 sandbox 계약 테스트
  → 배포 전 실행

staging smoke test
  → 실제 배포 구성, 가짜 결제 계정으로 실행

production smoke/canary
  → 읽기 전용 health와 최소 안전 경로만 확인
```

운영환경 테스트의 핵심은 “운영 시스템에 테스트 데이터를 보내는 것”이 아니라 “운영과 같은 프로파일·빈·직렬화·DB 종류를 일회용 인프라에서 검증하는 것”입니다.

## 5. 현재 테스트가 보장하지 않는 경계

자동 테스트가 많아도 가능한 모든 실패를 증명할 수는 없습니다. 현재 남은 중요한 운영 경계는 다음과 같습니다.

- 동일 HTTP 요청 재전송에 대한 주문·결제 멱등성
- 커밋 직후 프로세스 종료에도 알림을 보존하는 트랜잭셔널 아웃박스
- PostgreSQL 실제 격리 수준과 드라이버 차이를 확인하는 Testcontainers 테스트
- 결제사 sandbox의 DNS, TLS, 인증, 실제 오류 본문 계약
- 외부 결제 승인 뒤 우리 DB 커밋이 실패하는 분산 트랜잭션 보상

이 항목들은 단위 테스트를 더 늘리는 것보다 멱등 키, 아웃박스, 보상 결제 같은 설계 변경과 일회용 외부 인프라가 먼저 필요합니다.

## 6. 실행 명령

전체 테스트:

```bash
./gradlew test
```

단위 테스트 예시:

```bash
./gradlew test --tests '*ProductTest'
./gradlew test --tests '*PurchaseOrderTest'
./gradlew test --tests '*FakePaymentClientTest'
./gradlew test --tests '*OrderServiceUnitTest'
```

프로파일과 운영 구성만 실행:

```bash
./gradlew test --tests '*PaymentClientProfileTest'
./gradlew test --tests '*ProdProfileContextTest'
./gradlew test --tests '*ExternalPaymentClientTest'
```

테스트 리포트:

```text
build/reports/tests/test/index.html
```
