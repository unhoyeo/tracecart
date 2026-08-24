# TraceCart

Logback, MDC, Spring Profiles를 주문·재고·결제 장애 추적에 적용한 Java 21 포트폴리오 프로젝트입니다.

한 주문의 HTTP 요청, 짧게 분리된 DB 트랜잭션, 외부 결제, 커밋 이후 비동기 알림을 같은 `traceId`와 `orderId`로 연결합니다. local/dev에서는 요청 헤더로 성공·거절·타임아웃을 재현하고, prod에서는 실제 HTTP 결제 구현만 활성화합니다.

> 코드 실행 순서는 [`docs/CODE_WALKTHROUGH.md`](docs/CODE_WALKTHROUGH.md), 테스트 계층과 운영 검증 방법은 [`docs/TESTING_GUIDE.md`](docs/TESTING_GUIDE.md)를 먼저 보세요.

## 기술 스택

- Java 21, Spring Boot 4.1.1, Gradle 9.7.1
- Spring MVC, Validation, Data JPA, Actuator, RestClient
- Logback + Spring Boot Structured Logging + MDC
- Flyway
- H2(local/test), PostgreSQL(dev/prod)
- Testcontainers PostgreSQL

## 핵심 처리 흐름

```text
TraceIdFilter
  └─ OrderController
       ├─ HTTP DTO → CreateOrderCommand
       └─ OrderService
            ├─ 짧은 트랜잭션 1: 멱등 주문 PENDING 생성 + 재고 예약
            ├─ 짧은 트랜잭션 2: PAYMENT_PROCESSING 선점
            ├─ 트랜잭션 밖: PaymentClient 호출
            └─ 짧은 트랜잭션 3
                 ├─ 승인 → PAID + 거래 ID + 커밋 후 알림
                 ├─ 명시적 거절 → PAYMENT_DECLINED + 재고 복원
                 └─ 타임아웃/통신 장애 → PAYMENT_UNKNOWN + 재고 예약 유지
```

외부 결제 중에는 상품 행 잠금과 DB 커넥션을 유지하지 않습니다. `Idempotency-Key`와 주문의 결제 선점 상태로 같은 요청의 중복 주문·결제를 막습니다.

## 실행

JDK 21에서 다음 명령을 실행합니다.

```bash
./gradlew bootRun
```

기본 프로파일은 `local`입니다. Flyway가 H2 스키마를 만들고 데모 상품 세 개를 적재합니다.

```bash
curl http://localhost:8080/api/products
```

성공 주문:

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -H 'X-Trace-Id: portfolio-demo-0001' \
  -H 'Idempotency-Key: order-request-0001' \
  -H 'X-Demo-Payment-Scenario: SUCCESS' \
  -d '{
    "userId": "user-100",
    "productId": 1,
    "quantity": 2
  }'
```

IntelliJ에서는 [`http/tracecart.http`](http/tracecart.http)를 열면 성공, 재전송, 거절, 타임아웃과 예외 요청을 순서대로 실행할 수 있습니다.

## API 상태 정책

| 결과 | HTTP | 주문 상태 | 재고 |
|---|---:|---|---|
| 최초 결제 승인 | 201 | `PAID` | 차감 유지 |
| 승인 주문 멱등 재전송 | 200 | `PAID` | 추가 차감 없음 |
| 명시적 결제 거절 | 422 | `PAYMENT_DECLINED` | 복원 |
| 타임아웃·통신 장애 | 202 | `PAYMENT_UNKNOWN` | 예약 유지 |
| 재고 부족·멱등 키 오용 | 409 | 생성 안 함 | 변경 없음 |

타임아웃은 실패가 아니라 결과를 모르는 상태입니다. 실제 결제사가 승인했을 가능성이 있으므로 즉시 재결제하거나 재고를 풀지 않습니다.

## 요청 헤더

- `X-Trace-Id`: 선택 사항입니다. 안전한 8~64자 값이면 재사용하고 아니면 서버가 새 ID를 발급해 응답 헤더에 돌려줍니다.
- `Idempotency-Key`: 주문 생성에 필수인 8~64자 키입니다. 동일 요청 재전송에는 같은 키를 사용해야 합니다.
- `X-Demo-Payment-Scenario`: local/dev/test 전용이며 `SUCCESS`, `FAILURE`, `TIMEOUT`을 받습니다. prod에서는 이 헤더를 거절합니다.

`X-User-Id`는 신뢰할 수 없는 클라이언트 헤더이므로 사용하지 않습니다. 현재 데모는 검증된 요청 본문의 사용자 ID를 MDC에 넣으며, 인증을 추가한다면 Spring Security principal을 사용해야 합니다.

## 프로파일

| 프로파일 | DB | 결제 구현 | 로그 |
|---|---|---|---|
| local | H2 | Fake | 읽기 쉬운 컬러 콘솔 |
| dev | PostgreSQL | Fake | 텍스트 콘솔 + JSON 롤링 파일 |
| test | H2 또는 Testcontainers | Fake/Mock | 최소 콘솔 |
| prod | PostgreSQL | External HTTP | Logstash JSON stdout |

Fake 결제는 `local | dev | test`에서만 활성화됩니다. `prod`와 다른 프로파일을 동시에 켜거나 알 수 없는 프로파일을 사용하면 기동에 실패합니다.

개발 PostgreSQL:

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

운영 필수 환경변수:

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://host:5432/tracecart
DB_USERNAME=...
DB_PASSWORD=...
PAYMENT_BASE_URL=https://payment.example.com
```

## 로그 설계

local 완료 로그에는 상태와 처리 시간이 직접 보입니다.

```text
HTTP request completed: status=201, elapsedMs=84
```

dev/prod 구조화 로그에서는 `status`와 `elapsedMs`가 숫자 key-value로 기록되고 MDC의 `traceId`, `orderId`, `userId`, `httpMethod`, `requestUri`가 JSON 필드로 들어갑니다.

`MdcTaskDecorator`는 요청의 MDC를 `notification-*` 스레드로 복사하고 작업 후 원래 상태를 복원합니다. 알림은 `AFTER_COMMIT` 이벤트이므로 결제 완료 트랜잭션이 롤백되면 실행되지 않습니다.

운영 결제 요청에는 같은 `X-Trace-Id`와 `Idempotency-Key`를 전달합니다. 비밀번호, 토큰, 카드번호, 원문 외부 응답은 로그에 기록하지 않습니다.

## DB 스키마

Flyway의 [`V1__create_order_schema.sql`](src/main/resources/db/migration/V1__create_order_schema.sql)이 모든 환경의 테이블과 제약조건을 생성합니다. Hibernate는 `ddl-auto=validate`만 수행하므로 애플리케이션이 임의로 운영 스키마를 수정하지 않습니다.

## 테스트

```bash
./gradlew test
```

Docker가 실행 중이면 PostgreSQL Testcontainers 테스트도 수행합니다. Docker가 없으면 해당 두 테스트만 건너뛰고 H2 기반 단위·통합·프로파일·로그 테스트는 계속 실행합니다.

주요 검증 범위:

- 결제 성공·명시적 거절·타임아웃·통신 장애 분류
- 타임아웃 재고 예약 유지와 거절 재고 복원
- 주문 멱등 재전송과 키 오용 충돌
- 외부 결제의 추적 ID·멱등성 키 전달
- 트랜잭션 분리와 커밋 후 비동기 알림
- H2 및 실제 PostgreSQL 낙관적 잠금
- MDC 중첩·예외 정리·비동기 전파
- local/dev/prod Logback 설정과 구조화 완료 로그
- 프로파일 선택·상충 방지·설정값 검증
- Flyway 스키마와 JPA 엔티티 정합성

## 남은 운영 과제

- `PAYMENT_UNKNOWN` 주문을 결제사 조회 API로 대사하는 스케줄러
- 결제 승인 뒤 DB 장애가 지속될 때의 재처리·보상 정책
- 프로세스 종료에도 알림을 보존하는 트랜잭셔널 아웃박스
- Spring Security 인증 principal 기반 사용자 식별
- Micrometer Tracing과 W3C `traceparent`

이 항목들은 현재 프로젝트의 로그·MDC·멀티프로파일 학습 범위를 넘어가므로 다음 확장 단계로 남겨 두었습니다.
