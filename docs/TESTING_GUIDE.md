# TraceCart 테스트 전략

테스트는 실패 원인을 빠르게 찾고 H2의 거짓 양성을 줄이기 위해 계층을 나눕니다.

```text
순수 단위 테스트
  → Spring + H2 + Flyway 통합 테스트
  → 프로파일·설정·로그 계약 테스트
  → Mock HTTP 운영 결제 계약 테스트
  → 실제 PostgreSQL Testcontainers 테스트
```

## 1. 단위 테스트

| 영역 | 검증 내용 |
|---|---|
| Product | 가격·이름·재고 불변식, 차감·복원·부족 |
| PurchaseOrder | 멱등 요청 비교와 허용·거절 상태 전이 |
| OrderService | 결제 조율, 재전송, 선점 경쟁, 예상 밖 오류 |
| FakePaymentClient | 성공·거절·타임아웃·인터럽트 |
| ExternalPaymentClient | 2xx, 402, 5xx, 타임아웃, 깨진 응답 |
| MdcScope·Decorator | 중첩 복구, 예외 정리, 스레드 전파 |
| 설정·프로파일 | 풀 크기·URL 검증, 상충 프로파일 거절 |

OrderService 단위 테스트에서는 트랜잭션 서비스와 결제 클라이언트를 mock으로 바꿔 외부 결제가 DB 트랜잭션 메서드 사이에서 실행되는 분기만 확인합니다.

## 2. H2 통합 테스트

`@SpringBootTest`와 `test` 프로파일을 사용합니다. Flyway가 H2에 운영과 같은 마이그레이션을 적용하고 Hibernate는 `validate`로 정합성을 확인합니다.

주요 테스트:

- 성공 주문·거절·타임아웃의 실제 DB 상태
- 거절 재고 복원과 타임아웃 예약 유지
- 동일 멱등 키 재전송과 다른 내용 재사용 충돌
- API의 200/201/202/400/404/409/422 계약
- 결제 완료 커밋 뒤 비동기 알림
- 낙관적 잠금
- 실제 스레드 풀의 MDC 전달

## 3. 운영 결제 계약 테스트

`ExternalPaymentClientTest`는 네트워크를 사용하지 않고 `MockRestServiceServer`를 RestClient에 연결합니다.

검증 항목:

1. POST `/payments` 요청 JSON
2. `Idempotency-Key` 전달
3. `X-Trace-Id` 전달
4. 거래 ID 역직렬화
5. 결제 거절을 `DECLINED`으로 분류
6. 서버 장애를 `UNAVAILABLE`로 분류
7. 네트워크 타임아웃을 `TIMEOUT`으로 분류
8. 빈·누락·공백·깨진 응답을 `INVALID_RESPONSE`로 분류
9. 원인 예외 보존

`ProdProfileContextTest`는 실제 prod 프로파일을 켜되 DB와 결제 주소만 안전한 테스트 값으로 교체합니다. ExternalPaymentClient 단독 선택, Fake 미등록, 운영 RestClient 제한 시간, JSON Logback 설정 파싱을 확인합니다.

## 4. PostgreSQL Testcontainers

`PostgreSqlOrderIntegrationTest`는 Docker가 실행 중일 때 `postgres:17-alpine` 컨테이너를 생성합니다.

- PostgreSQL에서 Flyway V1 적용
- 실제 DB 제품 확인
- 멱등 재전송 시 결제 1회
- PostgreSQL MVCC 환경의 낙관적 잠금 충돌

Docker가 없으면 `@Testcontainers(disabledWithoutDocker = true)`에 의해 이 테스트만 건너뜁니다. CI에서는 Docker를 제공해 반드시 실행하는 것을 권장합니다.

## 5. 로그 테스트

- `TraceIdFilterTest`: traceId 재사용·교체, 위조 user 헤더 무시, MDC 복구
- 완료 이벤트의 메시지에 status·elapsedMs 노출
- 구조화 key-value의 숫자 타입 보존
- `LogbackProfileConfigurationTest`: local/dev/prod appender와 롤링 경로
- `ProdProfileContextTest`: 실제 prod JSON Logback 설정 파싱

## 6. 운영환경 검증 단계

```text
매 커밋
  → 단위 + H2 + 프로파일 + Mock HTTP

Docker 사용 CI
  → PostgreSQL Testcontainers

배포 전
  → 결제사 sandbox 계약 테스트

staging
  → 실제 Secret·DNS·TLS·방화벽·마이그레이션 검증

production
  → health와 안전한 읽기 경로 smoke/canary
```

실제 운영 DB에 자동 테스트 데이터를 넣는 것이 아니라 운영과 같은 코드·프로파일·DB 종류를 일회용 환경에서 검증하는 것이 핵심입니다.

## 7. 실행 명령

전체 테스트:

```bash
./gradlew clean test
```

핵심 단위 테스트:

```bash
./gradlew test --tests '*OrderServiceUnitTest'
./gradlew test --tests '*PurchaseOrderTest'
./gradlew test --tests '*MdcScopeTest'
```

운영 구성:

```bash
./gradlew test --tests '*ProdProfileContextTest'
./gradlew test --tests '*ExternalPaymentClientTest'
```

PostgreSQL:

```bash
./gradlew test --tests '*PostgreSqlOrderIntegrationTest'
```

HTML 리포트는 `build/reports/tests/test/index.html`에서 확인합니다.

## 8. 아직 자동화하지 않은 경계

- 실제 결제사 sandbox의 인증·DNS·TLS
- `PAYMENT_UNKNOWN` 자동 대사
- 결제 성공 뒤 장기 DB 장애의 재처리
- 프로세스 강제 종료 시 인메모리 알림 유실

이 경계는 테스트 추가만으로 해결되지 않으며 결제 조회 API, 재처리 워커, 트랜잭셔널 아웃박스 같은 설계 확장이 필요합니다.
