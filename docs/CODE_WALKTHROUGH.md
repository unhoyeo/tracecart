# TraceCart 코드 읽기 순서

파일 이름순이 아니라 HTTP 요청이 실제로 실행되는 순서로 읽기 위한 안내서입니다. Java 코드와 설정에는 “무엇”보다 “왜”를 이해할 수 있도록 한글 주석을 유지합니다.

## 0. 실행 환경과 DB 스키마

먼저 다음 파일을 읽습니다.

1. `application.yml`
2. `application-local.yml`, `application-dev.yml`, `application-prod.yml`
3. `db/migration/V1__create_order_schema.sql`
4. `logback-spring.xml`

공통 설정에 활성 프로파일 설정이 합쳐지고, Flyway가 테이블을 만든 뒤 Hibernate가 엔티티와 스키마가 같은지 검증합니다. `ActiveProfileValidator`는 local/dev/test/prod 중 정확히 하나만 허용합니다.

## 1. 애플리케이션 시작

`TraceCartApplication.main()`이 Spring 컨테이너를 시작합니다.

```text
컴포넌트 탐색
→ @ConfigurationProperties 바인딩과 검증
→ 프로파일별 PaymentClient 선택
→ Flyway 마이그레이션
→ JPA 스키마 검증
→ 비동기 스레드 풀 생성
→ 내장 Tomcat 시작
```

## 2. 요청 traceId 만들기

`TraceIdFilter.doFilterInternal()`이 Controller보다 먼저 실행됩니다.

```text
X-Trace-Id 확인
  ├─ 안전한 8~64자 → 재사용
  └─ 누락·잘못된 값 → 서버가 32자 ID 생성
          ↓
MDC에 traceId, httpMethod, requestUri 저장
          ↓
filterChain.doFilter()
          ↓
status, elapsedMs 완료 로그
          ↓
필터 진입 전 MDC로 복구
```

`X-User-Id`는 신뢰하지 않습니다. 사용자 ID는 검증된 주문 명령을 만든 뒤 `OrderService`가 `MdcScope`로 넣습니다.

## 3. HTTP 입력을 애플리케이션 명령으로 변환

다음 순서로 읽습니다.

1. `CreateOrderRequest`
2. `OrderController`
3. `IdempotencyKey`, `OrderUserId`, `OrderQuantity`
4. `CreateOrderCommand`

Controller는 Bean Validation으로 JSON을 검증하고, `Idempotency-Key`를 값 객체로 바꾸고, local/dev/test 전용 `X-Demo-Payment-Scenario`를 해석합니다. 운영 프로파일의 `ProductionPaymentScenarioResolver`는 데모 시나리오 헤더를 거절합니다.

## 4. 주문 유스케이스와 트랜잭션 경계

`OrderService`와 `OrderTransactionService`를 번갈아 읽습니다.

```text
OrderService.create()
  ├─ REQUIRES_NEW: PENDING 주문 생성 + 재고 예약
  ├─ REQUIRES_NEW: PAYMENT_PROCESSING 선점
  ├─ 트랜잭션 없음: PaymentClient 호출
  └─ REQUIRES_NEW: 결과 확정
       ├─ 승인 → PAID
       ├─ 명시적 거절 → PAYMENT_DECLINED + 재고 복원
       └─ 타임아웃·장애 → PAYMENT_UNKNOWN + 재고 예약 유지
```

외부 결제 앞뒤의 트랜잭션을 별도 빈으로 분리한 이유는 같은 클래스 내부 호출로는 Spring 트랜잭션 프록시가 적용되지 않기 때문입니다. `REQUIRES_NEW`는 각 DB 단계를 실제 커밋 단위로 만듭니다.

처음 디버깅할 때 권장 중단점:

- `OrderTransactionService.placePendingOrder()`
- `orderRepository.saveAndFlush()`
- `OrderTransactionService.claimPayment()`
- `paymentClient.pay()`
- `OrderTransactionService.completePayment()`

## 5. 멱등성이 중복 처리를 막는 과정

```text
동일 키 + 동일 내용 → 기존 주문 반환, 재고·결제 재실행 없음
동일 키 + 다른 내용 → 409 IDEMPOTENCY_KEY_REUSED
동시 INSERT 경쟁 → DB UNIQUE 제약의 승자 주문을 다시 조회
```

주문을 결제하기 전 `PAYMENT_PROCESSING`으로 선점하므로 같은 주문을 두 요청이 동시에 결제하지 않습니다. 운영 결제사에도 같은 키를 전달해 프로세스 경계를 넘는 중복 승인까지 방어합니다.

## 6. 도메인 객체가 지키는 규칙

`Product`는 가격·재고와 재고 차감·복원을 책임집니다. 재고 부족 시 HTTP를 모르는 `InsufficientStockException`을 던지고, HTTP 409 변환은 `GlobalExceptionHandler`가 담당합니다.

`PurchaseOrder`는 다음 전이만 허용합니다.

```text
PENDING → PAYMENT_PROCESSING
PAYMENT_PROCESSING → PAID
PAYMENT_PROCESSING → PAYMENT_DECLINED
PAYMENT_PROCESSING → PAYMENT_UNKNOWN
PAYMENT_UNKNOWN → PAID
```

마지막 전이는 타임아웃 뒤 결제사 대사로 실제 승인을 확인하는 확장 경로입니다.

## 7. 프로파일별 결제 구현

```text
local/dev/test → FakePaymentClient
prod           → ExternalPaymentClient
그 외          → PaymentClient 없음, 기동 실패
```

`ExternalPaymentClient`는 결제 오류를 `DECLINED`, `TIMEOUT`, `UNAVAILABLE`, `INVALID_RESPONSE`로 분류하고 원인 예외를 보존합니다. 외부 요청에는 `X-Trace-Id`와 `Idempotency-Key`를 전달하지만 Fake 전용 시나리오는 보내지 않습니다.

## 8. 결제 결과와 HTTP 상태

`OrderController.responseStatus()`는 최초 `PAID`를 201, 멱등 재조회 `PAID`를 200, `PAYMENT_DECLINED`를 422, `PAYMENT_UNKNOWN`을 202로 변환합니다.

타임아웃은 승인 여부를 모르므로 실패로 단정하지 않습니다. 재고도 중복 판매를 막기 위해 예약 상태로 유지합니다.

## 9. 커밋 뒤 비동기 알림과 MDC

읽는 순서:

1. `OrderPaidEvent`
2. `OrderPaidNotificationListener`
3. `AsyncConfig`
4. `MdcTaskDecorator`
5. `MdcScope`
6. `NotificationService`

`completePayment()`이 트랜잭션 안에서 이벤트를 발행하고 실제 커밋에 성공해야 `AFTER_COMMIT` 리스너가 실행됩니다. `MdcTaskDecorator`는 제출 시점의 traceId를 알림 스레드로 복사하며, 이벤트의 orderId와 userId는 리스너가 새 스코프로 넣습니다.

## 10. 로그와 예외

- local: 사람이 읽는 텍스트
- dev: 텍스트 콘솔 + 설정 가능한 JSON 롤링 파일
- prod: JSON stdout

요청 완료 로그는 메시지에도 `status`와 `elapsedMs`를 표시하고 구조화 key-value에는 숫자 타입으로 저장합니다. `GlobalExceptionHandler`는 검증·404·409·깨진 JSON·500 응답을 같은 `ApiError` 구조와 traceId로 반환합니다.

## IntelliJ 디버깅 순서

1. `TraceCartApplication`을 Debug로 실행합니다.
2. 위 트랜잭션·결제 중단점을 설정합니다.
3. `http/tracecart.http`의 성공 요청을 실행합니다.
4. `OrderService` 자체에는 트랜잭션이 없고 `OrderTransactionService` 진입마다 새 트랜잭션이 생기는지 확인합니다.
5. 같은 멱등성 키를 다시 보내 결제 중단점을 지나지 않는지 확인합니다.
6. 거절과 타임아웃 요청에서 재고 복원 여부가 다른지 확인합니다.
7. `NotificationService` 중단점에서 스레드 이름과 MDC 값을 확인합니다.
