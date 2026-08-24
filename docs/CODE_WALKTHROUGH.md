# TraceCart 코드 읽기 순서

이 문서는 파일 이름순이 아니라 **HTTP 요청이 실제로 실행되는 순서**로 코드를 읽기 위한 안내서입니다. 소스의 각 의미 있는 실행문과 설정 바로 위에는 같은 관점의 한글 주석을 달았습니다. `package`, `import`, 닫는 괄호처럼 동작을 만들지 않는 문법은 반복 설명에서 제외했습니다.

## 0. 먼저 실행 환경을 고르기

1. `src/main/resources/application.yml`
2. `src/main/resources/application-local.yml`
3. `src/main/resources/application-dev.yml`
4. `src/main/resources/application-prod.yml`
5. `src/main/resources/logback-spring.xml`

`application.yml`은 공통값이고, 활성 프로파일의 파일이 그 값을 추가하거나 덮어씁니다. 아무것도 지정하지 않으면 `local`이 선택되어 H2와 Fake 결제를 사용합니다.

## 1. 애플리케이션 시작

읽을 파일: `TraceCartApplication.java`

`main()` → `SpringApplication.run()` 순서로 실행됩니다. 이때 Spring은 다음 일을 합니다.

1. `@Component`, `@Service`, `@RestController` 클래스를 찾습니다.
2. 선택된 프로파일에 맞는 빈만 만듭니다.
3. 데이터베이스와 JPA를 연결합니다.
4. `AsyncConfig`의 스레드 풀을 만듭니다.
5. 내장 Tomcat을 시작합니다.

## 2. HTTP 요청에 traceId 붙이기

읽을 파일: `common/logging/TraceIdFilter.java`

주문 API보다 먼저 실행됩니다.

```text
X-Trace-Id 헤더 확인
  ├─ 안전한 값 → 그대로 사용
  └─ 없거나 위험한 값 → 새 UUID 생성
           ↓
MDC에 traceId, method, URI 저장
           ↓
Controller로 요청 전달
           ↓
상태 코드와 처리 시간 로그
           ↓
MDC 원상 복구
```

중요한 중단점은 `filterChain.doFilter(request, response)`입니다. 이 줄 안에서 Controller, Service, Repository까지 모두 실행된 후 다시 필터로 돌아옵니다.

## 3. JSON을 Java 요청 객체로 바꾸기

읽을 파일:

1. `order/api/CreateOrderRequest.java`
2. `order/api/OrderController.java`

Spring MVC가 JSON을 `CreateOrderRequest`로 변환하고 `@Valid` 검증을 실행합니다. 검증이 성공하면 Controller는 업무 처리를 `OrderService.create()`에 위임합니다.

## 4. 주문 유스케이스 따라가기

읽을 파일: `order/application/OrderService.java`

`create()` 메서드의 실행 순서는 다음과 같습니다.

1. Controller를 거치지 않은 호출도 안전하도록 요청을 다시 검증합니다.
2. `userId`를 MDC에 넣습니다.
3. 상품을 조회합니다.
4. 재고를 검사하고 차감합니다.
5. 단가 × 수량으로 총액을 계산합니다.
6. PENDING 주문을 저장해 주문 ID를 얻습니다.
7. 주문 ID를 MDC에 넣습니다.
8. 프로파일에 맞는 결제 구현을 호출합니다.
9. 성공이면 PAID와 `OrderPaidEvent`, 실패면 PAYMENT_FAILED와 재고 복원을 실행합니다.
10. 엔티티를 `OrderResponse`로 바꿔 Controller에 반환합니다.
11. 트랜잭션이 실제 커밋된 경우에만 비동기 리스너가 알림을 보냅니다.

처음 디버깅할 때는 다음 줄에 중단점을 거는 것이 좋습니다.

- `productRepository.findById(...)`
- `product.decreaseStock(...)`
- `orderRepository.saveAndFlush(...)`
- `paymentClient.pay(...)`
- `notificationService.sendOrderCompleted(...)`

## 5. 엔티티가 스스로 지키는 규칙 보기

읽을 파일:

1. `product/Product.java`
2. `order/domain/PurchaseOrder.java`
3. `order/domain/OrderStatus.java`

Service가 필드 값을 직접 바꾸지 않고 `decreaseStock()`, `restoreStock()`, `markPaid()`, `markPaymentFailed()`를 호출합니다. 상태 변경 규칙을 엔티티 안에 모아 두면 잘못된 변경 경로를 줄일 수 있습니다.

## 6. 멀티프로파일이 구현체를 바꾸는 지점

읽을 파일:

1. `payment/PaymentClient.java`
2. `payment/FakePaymentClient.java`
3. `payment/ExternalPaymentClient.java`

`OrderService`는 `PaymentClient` 인터페이스만 압니다.

```text
local / dev / test → @Profile("!prod") → FakePaymentClient
prod               → @Profile("prod")  → ExternalPaymentClient
```

따라서 주문 로직을 수정하지 않고 실행 환경만 바꿔 결제 방식을 교체할 수 있습니다.

## 7. 비동기 알림에서도 같은 MDC가 보이는 이유

다음 순서로 읽습니다.

1. `notification/OrderPaidEvent.java`
2. `notification/OrderPaidNotificationListener.java`
3. `notification/NotificationService.java`
4. `common/config/AsyncConfig.java`
5. `common/logging/MdcTaskDecorator.java`
6. `common/logging/MdcScope.java`

`@TransactionalEventListener(AFTER_COMMIT)`은 주문 트랜잭션이 성공적으로 끝난 뒤에만 이벤트를 받습니다. 리스너의 `@Async` 때문에 실제 알림은 요청 스레드가 아니라 `notification-*` 스레드에서 실행됩니다. MDC는 기본적으로 스레드 사이에 자동 전달되지 않으므로 `MdcTaskDecorator`가 제출 시점의 `traceId`를 복사하고, 리스너가 이벤트의 `orderId`와 `userId`를 다시 넣습니다. 실행이 끝나면 각각 이전 상태로 복구해 다음 알림에 사용자 정보가 새지 않게 합니다.

## 8. 로그가 화면과 파일에서 달라지는 이유

읽을 파일: `src/main/resources/logback-spring.xml`

- local: 사람이 읽기 좋은 컬러 콘솔 패턴
- dev: 텍스트 콘솔 + Logstash JSON 롤링 파일
- prod: 수집기가 읽기 좋은 Logstash JSON stdout

`%X{traceId}`는 `MDC.get("traceId")`와 같은 값을 로그 패턴에서 꺼내는 표현입니다. 구조화 로그 encoder는 MDC Map을 JSON 필드로 자동 포함합니다.

## 9. 오류가 JSON 응답이 되는 과정

다음 순서로 읽습니다.

1. `common/exception/BusinessException.java`
2. `common/exception/GlobalExceptionHandler.java`
3. `common/exception/ApiError.java`

예를 들어 상품이 없으면 Service가 `BusinessException`을 던집니다. `GlobalExceptionHandler`가 이를 잡아 404 상태와 `PRODUCT_NOT_FOUND`, 현재 MDC의 `traceId`를 가진 `ApiError`로 변환합니다.

## 10. 테스트로 이해 확인하기

다음 순서로 실행합니다.

```bash
./gradlew test --tests '*TraceIdFilterTest'
./gradlew test --tests '*MdcTaskDecoratorTest'
./gradlew test --tests '*OrderServiceIntegrationTest'
./gradlew test --tests '*OrderApiIntegrationTest'
```

테스트 코드에는 `Given → When → Then` 주석을 달아 준비, 실행, 검증의 경계를 표시했습니다.

## IntelliJ에서 한 요청씩 디버깅하기

1. `TraceCartApplication.main()` 왼쪽 실행 아이콘에서 **Debug**를 선택합니다.
2. `TraceIdFilter.doFilterInternal()`의 첫 줄에 중단점을 겁니다.
3. README의 성공 주문 `curl`을 실행합니다.
4. **Step Over**로 이동하며 IntelliJ의 Variables 창에서 `traceId`, `product`, `order`, `command` 값을 봅니다.
5. `paymentClient`의 실제 타입이 `FakePaymentClient`인지 확인합니다.
6. 비동기 알림은 다른 스레드이므로 `NotificationService.sendOrderCompleted()`에도 중단점을 겁니다.

이 순서대로 한 번 성공 요청을 따라간 뒤 `paymentScenario`만 `FAILURE`로 바꾸면 성공과 실패 흐름의 차이를 가장 빠르게 이해할 수 있습니다.
