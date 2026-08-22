package com.example.tracecart.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

// 비동기 스레드로 MDC를 복사하고 작업 뒤 복원하는지를 검증합니다.
class MdcTaskDecoratorTest {

    // Spring 컨테이너 없이 직접 테스트할 대상 객체를 만듭니다.
    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    // 각 테스트 뒤 현재 JUnit 스레드의 MDC가 다음 테스트에 영향을 주지 않게 합니다.
    @AfterEach
    void clearMdc() {
        // 현재 스레드의 모든 MDC 키를 제거합니다.
        MDC.clear();
    }

    // 요청 MDC 전달과 풀 스레드 MDC 복원을 한 테스트에서 확인합니다.
    @Test
    void copiesCallerContextAndRestoresExecutorContext() {
        // Given: 작업을 제출하는 요청 스레드에 traceId가 있다고 가정합니다.
        MDC.put("traceId", "caller-trace");
        // When 내부에서 관찰한 MDC 값을 테스트 밖으로 꺼낼 안전한 상자입니다.
        AtomicReference<String> observed = new AtomicReference<>();
        // decorate 시점에 caller-trace를 복사하고, 실행할 작업을 감쌉니다.
        Runnable decorated = decorator.decorate(() -> observed.set(MDC.get("traceId")));

        // 실제 풀 스레드가 이전 작업에서 다른 값을 가졌던 상황을 흉내 냅니다.
        MDC.put("traceId", "executor-trace");
        // When: 장식된 비동기 작업을 실행합니다.
        decorated.run();

        // Then: 작업 안에서는 요청 스레드의 caller-trace가 보여야 합니다.
        assertThat(observed.get()).isEqualTo("caller-trace");
        // Then: 작업이 끝난 뒤에는 풀 스레드의 원래 값으로 복구돼야 합니다.
        assertThat(MDC.get("traceId")).isEqualTo("executor-trace");
    }
}
