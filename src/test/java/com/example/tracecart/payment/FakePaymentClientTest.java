package com.example.tracecart.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tracecart.common.config.AppProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Spring 없이 Fake 결제의 성공·실패·타임아웃 분기를 직접 호출하는 단위 테스트입니다.
class FakePaymentClientTest {

    private FakePaymentClient paymentClient;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                new AppProperties.Async(1, 1, 1),
                new AppProperties.Payment(0, 0, "http://localhost")
        );
        paymentClient = new FakePaymentClient(properties);
    }

    @Test
    void returnsTransactionIdForSuccessScenario() {
        PaymentResult result = paymentClient.pay(command(PaymentScenario.SUCCESS));

        assertThat(result.transactionId()).startsWith("fake-");
    }

    @Test
    void throwsRejectedExceptionForFailureScenario() {
        assertThatThrownBy(() -> paymentClient.pay(command(PaymentScenario.FAILURE)))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("거절");
    }

    @Test
    void throwsTimeoutExceptionForTimeoutScenario() {
        assertThatThrownBy(() -> paymentClient.pay(command(PaymentScenario.TIMEOUT)))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("초과");
    }

    @Test
    void restoresInterruptedFlagWhenDelayIsInterrupted() {
        try {
            // 현재 테스트 스레드에 인터럽트 상태를 설정해 Thread.sleep의 중단 분기를 재현합니다.
            Thread.currentThread().interrupt();

            assertThatThrownBy(() -> paymentClient.pay(command(PaymentScenario.SUCCESS)))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("중단");
            // 호출자가 중단 사실을 계속 감지할 수 있도록 인터럽트 플래그가 복구돼야 합니다.
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // 다음 JUnit 테스트에 인터럽트 상태가 누출되지 않도록 현재 플래그를 읽고 지웁니다.
            Thread.interrupted();
        }
    }

    private PaymentCommand command(PaymentScenario scenario) {
        return new PaymentCommand(100L, "user-1", new BigDecimal("10000.00"), scenario);
    }
}
