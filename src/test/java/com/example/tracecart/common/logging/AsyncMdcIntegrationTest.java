package com.example.tracecart.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.ActiveProfiles;

// 실제 AsyncConfig의 스레드 풀과 TaskDecorator를 함께 사용해 MDC 전파를 검증합니다.
@SpringBootTest
@ActiveProfiles("test")
class AsyncMdcIntegrationTest {

    @Autowired
    @Qualifier("applicationTaskExecutor")
    TaskExecutor taskExecutor;

    @AfterEach
    void clearCallerMdc() {
        MDC.clear();
    }

    @Test
    void configuredExecutorCopiesRequestMdcToWorkerThread() throws InterruptedException {
        MDC.put("traceId", "async-trace-1234");
        MDC.put("orderId", "100");
        AtomicReference<String> observedTraceId = new AtomicReference<>();
        AtomicReference<String> observedOrderId = new AtomicReference<>();
        AtomicReference<String> workerThreadName = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);

        taskExecutor.execute(() -> {
            observedTraceId.set(MDC.get("traceId"));
            observedOrderId.set(MDC.get("orderId"));
            workerThreadName.set(Thread.currentThread().getName());
            completed.countDown();
        });

        assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(observedTraceId.get()).isEqualTo("async-trace-1234");
        assertThat(observedOrderId.get()).isEqualTo("100");
        assertThat(workerThreadName.get()).startsWith("notification-");
    }
}
