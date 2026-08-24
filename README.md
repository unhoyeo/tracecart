# TraceCart

Logback, MDC, Spring Profiles를 실제 장애 추적 문제에 적용한 주문 처리 포트폴리오 프로젝트입니다.

주문 요청이 재고, 결제, 비동기 알림을 통과하는 동안 같은 `traceId`를 유지합니다. 결제 실패와 타임아웃을 API 입력으로 재현하고, local/dev/prod 환경별로 로그 형식과 인프라 구현을 교체할 수 있습니다.

> 처음 코드를 읽는다면 [`docs/CODE_WALKTHROUGH.md`](docs/CODE_WALKTHROUGH.md)의 실행 순서를 먼저 따라가세요. 주요 Java 코드와 설정에는 각 줄의 목적을 설명하는 한글 주석이 포함되어 있습니다.
>
> 테스트 계층과 운영 프로파일 검증 방법은 [`docs/TESTING_GUIDE.md`](docs/TESTING_GUIDE.md)에 정리했습니다.

## 기술 스택

- Java 21
- Spring Boot 4.1.1
- Gradle 9.7.1 Wrapper
- Spring MVC, Validation, Data JPA, Actuator
- Logback + Spring Boot Structured Logging
- H2(local/test), PostgreSQL(dev/prod)

## 구조

```text
HTTP request
  └─ TraceIdFilter
       ├─ MDC: traceId, httpMethod, requestUri, userId
       └─ OrderService
            ├─ Product 재고 차감
            ├─ PaymentClient
            │    ├─ FakePaymentClient (!prod)
            │    └─ ExternalPaymentClient (prod)
            └─ OrderPaidEvent
                 └─ AFTER_COMMIT 비동기 리스너
                      ├─ NotificationService
                      └─ MdcTaskDecorator가 traceId 복사 및 복원
```

핵심 패키지는 다음과 같습니다.

```text
com.example.tracecart
├── common
│   ├── config       # 비동기 실행기 및 타입 안전 설정
│   ├── exception    # 일관된 오류 응답
│   └── logging      # 요청 MDC, 스코프, 비동기 전파
├── order            # 주문 API, 애플리케이션, 도메인
├── payment          # 프로파일별 결제 클라이언트
├── product          # 상품과 재고
└── notification     # 비동기 알림
```

## 실행

JDK 21이 필요합니다.

```bash
./gradlew bootRun
```

기본 프로파일은 `local`이며 H2와 Fake 결제를 사용합니다. 기동 후 상품을 조회합니다.

```bash
curl http://localhost:8080/api/products
```

성공 주문을 생성합니다.

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -H 'X-Trace-Id: portfolio-demo-0001' \
  -d '{
    "userId": "user-100",
    "productId": 1,
    "quantity": 2,
    "paymentScenario": "SUCCESS"
  }'
```

`paymentScenario`를 `FAILURE` 또는 `TIMEOUT`으로 바꾸면 결제 장애, 주문 실패 기록, 재고 복원을 확인할 수 있습니다.

```bash
curl http://localhost:8080/api/orders/1
curl http://localhost:8080/actuator/health
```

## 프로파일

| 프로파일 | 데이터베이스 | 결제 구현 | 로그 |
|---|---|---|---|
| local | 인메모리 H2 | Fake | 컬러 패턴 콘솔, 애플리케이션 DEBUG |
| dev | PostgreSQL | Fake | 패턴 콘솔 + 일별/크기별 JSON 롤링 파일 |
| prod | PostgreSQL | 외부 HTTP | Logstash JSON stdout, INFO |
| test | 인메모리 H2 | Fake | 최소 콘솔 로그 |

개발 프로파일 실행:

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

운영 프로파일에 필요한 환경변수:

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://host:5432/tracecart
DB_USERNAME=...
DB_PASSWORD=...
PAYMENT_BASE_URL=https://payment.example.com
```

## 로그 설계

`TraceIdFilter`는 안전한 `X-Trace-Id`만 수용하고, 값이 없거나 형식이 잘못되면 새 ID를 생성합니다. 처리 후 기존 MDC를 복원하여 톰캣 스레드 재사용 시 컨텍스트가 새 요청으로 누출되지 않게 합니다.

local 로그 예시:

```text
12:30:10.123 INFO [http-nio-8080-exec-1] [traceId=portfolio-demo-0001 orderId=1 userId=user-100] c.e.t.o.application.OrderService - Payment approved: transactionId=fake-...
12:30:10.125 INFO [notification-1] [traceId=portfolio-demo-0001 orderId=1 userId=user-100] c.e.t.notification.NotificationService - Order completion notification sent: orderId=1, recipient=user-100
```

dev와 prod의 구조화 로그에는 MDC의 모든 키가 JSON 필드로 들어가므로 Loki, Elasticsearch 등에서 `traceId`로 검색할 수 있습니다. 비밀번호, 토큰, 카드번호, 이메일 원문은 MDC 또는 업무 로그에 기록하지 않습니다.

## 테스트

```bash
./gradlew test
```

테스트하는 주요 위험은 다음과 같습니다.

- 요청 `traceId` 수용 및 비정상 값 교체
- 요청 종료 후 MDC 복원
- 비동기 작업으로 MDC 전달 후 실행기 컨텍스트 복원
- test 프로파일에서 Fake 결제 구현 선택
- 결제 성공 시 재고 차감 및 주문 완료
- 결제 실패 시 실패 주문 저장 및 재고 복원
- 주문 API의 응답 상태와 `X-Trace-Id` 헤더
- 성공·결제 거절·타임아웃의 단위 및 통합 흐름
- 0·음수·최대치 초과 수량, 잘못된 상품 ID, 필수값 누락, 깨진 JSON
- local/dev/test/prod 프로파일별 PaymentClient 선택
- prod 전체 컨텍스트와 RestClient.Builder 자동 설정
- 운영 프로파일의 연결 2초·읽기 3초 타임아웃 바인딩
- 운영 결제 HTTP 2xx, 5xx, 네트워크 타임아웃, 빈·누락·공백 응답 계약
- 동시 재고 차감 시 낙관적 잠금 충돌과 HTTP 409 변환
- 주문 트랜잭션 커밋 후에만 비동기 완료 알림 실행

## 설계상 트레이드오프

- 데모를 간결하게 유지하기 위해 주문 하나가 상품 하나만 가집니다.
- 결제 실패 주문을 남기기 위해 결제 예외를 주문 트랜잭션 안에서 상태로 변환합니다.
- 알림은 커밋 이후 이벤트로 실행되지만 현재 로그 기반 Fake 구현입니다. 프로세스가 커밋 직후 종료돼도 유실되지 않게 하려면 트랜잭셔널 아웃박스가 필요합니다.
- 같은 주문 요청의 중복 전송을 막는 멱등성 키는 아직 구현하지 않았습니다. 실제 결제를 붙이기 전에 반드시 추가해야 합니다.
- dev의 `ddl-auto=update`는 빠른 데모용입니다. 운영 배포에서는 Flyway 같은 명시적 스키마 마이그레이션으로 교체해야 합니다.

## 다음 확장 후보

1. Loki + Grafana에서 `traceId`, `orderId`, 실패 사유 대시보드 구성
2. Micrometer Tracing으로 W3C `traceparent`와 MDC 연결
3. 트랜잭셔널 아웃박스와 Kafka/RabbitMQ 기반 알림
4. Testcontainers로 PostgreSQL 프로파일 통합 테스트
5. 인증 principal에서 `userId`를 가져오고 로그 마스킹 정책 자동화
6. `Idempotency-Key`와 결제사 멱등 키를 이용한 중복 결제 방지
