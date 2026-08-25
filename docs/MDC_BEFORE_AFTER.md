# 실제 코드로 비교하는 MDC Before/After

이 비교에서 별도 실험 API나 가짜 before/after 구현은 사용하지 않습니다. `main`은 수정이 끝난 after이고, 각 before 브랜치는 `main`의 실제 코드에서 보호 로직 한 가지를 제거한 상태입니다.

## 브랜치 구성

| Before 브랜치 | 실제로 바뀌는 파일 | 재현되는 문제 | After |
|---|---|---|---|
| `experiment/before-mdc-scope` | `OrderService` | 서비스가 넣은 `userId/orderId`가 필요 이상으로 오래 유지됨 | `main`의 `MdcScope` |
| `experiment/before-mdc-thread-cleanup` | `TraceIdFilter` | A 요청의 사용자 ID가 재사용된 스레드의 B 로그에 노출됨 | `main`의 `restore(previousContext)` |
| `experiment/before-async-propagation` | `AsyncConfig` | `@Async` 알림 로그에서 요청 `traceId`가 사라짐 | `main`의 `MdcTaskDecorator` 등록 |
| `experiment/before-task-decorator-restore` | `MdcTaskDecorator` | 복사만 하고 복원하지 않아 풀 스레드에 이전 작업 값이 남음 | `main`의 교체 및 `finally` 복원 |

각 문제를 독립 브랜치로 둔 이유는 여러 결함이 한꺼번에 켜지면 로그 한 줄의 원인이 불분명해지기 때문입니다.

## 실제 실행 증거

2026-08-25에 네 before 브랜치와 `main`을 각각 실행해 같은 HTTP 요청으로 비교했습니다.

- 실제 로그 비교: `docs/evidence/mdc-real-before-after.txt`
- 브랜치별 회귀 테스트 결과: `docs/evidence/mdc-regression-tests.txt`

증거 파일에는 설명을 위해 만든 예시가 아니라 실행 터미널에서 관찰한 스레드 이름과 MDC 값이 그대로 들어 있습니다.

## 실행 순서

예를 들어 요청 스레드 오염을 비교하려면 다음 순서로 실행합니다.

```bash
git switch experiment/before-mdc-thread-cleanup
SPRING_PROFILES_ACTIVE=local,mdc-repro ./gradlew bootRun
```

`http/mdc-before-after.http`에서 해당 요청을 실행해 before 로그를 저장합니다. 애플리케이션을 종료한 뒤 after를 실행합니다.

```bash
git switch main
SPRING_PROFILES_ACTIVE=local,mdc-repro ./gradlew bootRun
```

같은 HTTP 요청을 다시 실행합니다. `mdc-repro` 프로파일은 HTTP 스레드와 비동기 스레드를 각각 하나로 제한하므로 스레드 재사용이 우연에 좌우되지 않습니다.

## 코드 차이 확인

GitHub 또는 터미널에서 실제 수정 코드만 비교합니다.

```bash
git diff main...experiment/before-mdc-thread-cleanup
git diff main...experiment/before-mdc-scope
git diff main...experiment/before-async-propagation
git diff main...experiment/before-task-decorator-restore
```

before 브랜치의 결함은 기존 보호 테스트도 실패시킵니다. 이것은 테스트를 망가뜨린 것이 아니라, 테스트가 실제 회귀를 잡는다는 증거로 사용할 수 있습니다.

```bash
./gradlew test --tests '*TraceIdFilterTest'
./gradlew test --tests '*OrderServiceUnitTest'
./gradlew test --tests '*AsyncMdcIntegrationTest'
./gradlew test --tests '*MdcTaskDecoratorTest'
```

## 블로그 서술 방식

“MDC를 적용했다”보다 다음 인과관계를 중심으로 씁니다.

1. 로그에서 사용자 또는 traceId가 잘못 보이는 현상을 먼저 제시합니다.
2. 두 로그의 스레드 이름이 같다는 사실로 스레드 재사용을 확인합니다.
3. MDC가 `ThreadLocal`이라는 점과 실제 누락된 코드 한두 줄을 연결합니다.
4. `main`의 수정 코드로 전환해 동일 요청을 다시 실행합니다.
5. 로그 정상화와 회귀 테스트 통과를 함께 증거로 제시합니다.

핵심은 TaskDecorator를 단순한 “복사 기능”이 아니라, 재사용되는 실행기 스레드의 상태를 작업 단위로 격리하는 경계로 설명하는 것입니다.
