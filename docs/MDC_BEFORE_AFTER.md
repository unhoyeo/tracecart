# 실제 코드로 비교하는 MDC Before/After

`main`은 수정이 완료된 After입니다. 각 Before 브랜치는 실제 애플리케이션 코드에서 보호 로직 하나만 제거합니다. 별도 실험 API나 가짜 Before/After 구현은 사용하지 않습니다.

## 장애별 실행 파일

| Before 브랜치 | 실제 변경 파일 | 전용 HTTP 파일 | 실행 프로파일 |
|---|---|---|---|
| `experiment/before-mdc-thread-cleanup` | `TraceIdFilter` | `http/mdc/01-request-thread-leak.http` | `local,mdc-http-repro` |
| `experiment/before-mdc-scope` | `OrderService` | `http/mdc/02-mdc-scope-restore.http` | `local` |
| `experiment/before-async-propagation` | `AsyncConfig` | `http/mdc/03-async-trace-loss.http` | `local` |
| `experiment/before-task-decorator-restore` | `MdcTaskDecorator` | `http/mdc/04-task-decorator-pool-leak.http` | `local,mdc-async-repro` |

한 파일에는 한 장애를 재현하는 요청만 들어 있습니다. 파일 상단에는 대상 브랜치, IntelliJ Active profiles, Before와 After에서 확인할 로그가 적혀 있습니다.

## IntelliJ 실행 방법

터미널 명령은 필수가 아닙니다. `Run > Edit Configurations...`에서 실험별 Spring Boot 실행 구성을 만들어 두면 실행 버튼으로 확인할 수 있습니다.

| IntelliJ 실행 구성 이름 | Active profiles | 용도 |
|---|---|---|
| `TraceCart - local` | `local` | MDC 스코프, 비동기 traceId 전파 |
| `TraceCart - HTTP leak` | `local,mdc-http-repro` | Tomcat 요청 스레드 재사용 오염 |
| `TraceCart - Async leak` | `local,mdc-async-repro` | TaskDecorator 풀 스레드 오염 |

브랜치를 바꾸면 실행 중인 JVM을 종료하고 다시 시작해야 합니다. 시작 로그에서 선택한 프로파일이 활성화됐는지 확인합니다.

## 1. 요청 스레드 재사용으로 인한 개인정보 오염

`experiment/before-mdc-thread-cleanup`의 `TraceIdFilter`는 요청 종료 후 MDC를 정리하지 않습니다.

1. A 요청이 `user-A`를 MDC에 기록합니다.
2. 실제 주문자가 `user-B`인 다음 요청이 같은 Tomcat 스레드를 사용합니다.
3. B 요청에는 사용자 헤더가 없으므로 이전 `user-A`가 그대로 남습니다.
4. B의 요청 시작·완료 로그가 A의 개인정보로 잘못 기록됩니다.

Before에서 입증할 내용은 다음 세 가지입니다.

- A와 B의 `traceId`는 다릅니다.
- A와 B의 HTTP 스레드 이름은 같습니다.
- B 주문 응답과 서비스 로그는 `user-B`인데 요청 시작·완료 로그에는 `user-A`가 붙습니다.

`mdc-http-repro`는 해결책이 아니라 재현 조건입니다. 운영에서는 여러 스레드 중 같은 스레드가 재사용될 때만 간헐적으로 나타나는 문제를 블로그에서 매번 재현하기 위해 HTTP 스레드를 하나로 제한합니다.

## 2. MdcScope 누락으로 인한 요청 주체 오기록

`experiment/before-mdc-scope`는 `OrderService`에서 `MdcScope` 대신 `MDC.put()`만 사용합니다.

- `X-User-Id=authenticated-user`는 접근 로그에서 유지해야 할 요청 주체입니다.
- `body.userId=order-user`는 주문 처리 구간에서 사용할 업무 사용자입니다.
- Before에서는 서비스가 끝난 뒤에도 `order-user/orderId`가 남아 요청 완료 로그의 주체가 바뀝니다.
- After에서는 `MdcScope.close()`가 `authenticated-user`를 복원하고 `orderId`를 제거합니다.

단순히 orderId가 남았다는 이야기가 아니라, 감사·접근 로그에서 실제 요청 주체가 다른 사용자로 기록될 수 있다는 문제입니다.

## 3. @Async 경계에서 traceId 실종

`experiment/before-async-propagation`은 실제 `AsyncConfig`에서 `MdcTaskDecorator` 등록을 제거합니다.

요청 로그에는 traceId가 있지만 비동기 알림 로그에는 traceId가 없습니다. 로그는 존재해도 주문 처리와 알림을 동일 요청으로 검색할 수 없는 것이 장애입니다. 이 실험은 스레드 개수를 제한할 필요가 없습니다.

## 4. 복원 없는 TaskDecorator의 풀 오염

`experiment/before-task-decorator-restore`는 호출자 MDC를 `put`으로 복사하지만, 실행 전 교체와 실행 후 복원을 하지 않습니다.

1. A 비동기 작업이 `header-user-A`를 notification 스레드에 남깁니다.
2. 사용자 헤더가 없는 B 작업이 같은 notification 스레드를 재사용합니다.
3. B 알림 진입 로그에 A의 사용자 ID가 붙습니다.

`mdc-async-repro`는 A와 B가 같은 비동기 스레드를 사용하도록 만드는 재현 조건입니다. After의 `MdcTaskDecorator`는 호출자 컨텍스트로 정확히 교체하고 `finally`에서 실행기 컨텍스트를 복원합니다.

## 비교 순서

1. Before 브랜치를 체크아웃합니다.
2. 전용 HTTP 파일에 적힌 IntelliJ 실행 구성으로 앱을 시작합니다.
3. 파일의 요청을 위에서 아래로 실행하고 로그를 저장합니다.
4. 앱을 종료하고 `main`으로 전환합니다.
5. 같은 실행 구성과 HTTP 파일로 다시 실행합니다.
6. `git diff main...브랜치명`으로 실제 보호 코드 차이를 확인합니다.

각 Before 브랜치에서는 기존 보호 테스트가 실패하고 `main`에서는 전체 테스트가 통과합니다. 이는 의도적인 결함을 테스트가 실제 회귀로 탐지한다는 증거입니다.
