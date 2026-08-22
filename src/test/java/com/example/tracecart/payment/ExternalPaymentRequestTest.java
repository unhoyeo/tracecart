package com.example.tracecart.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

// 운영 요청 DTO가 Fake 전용 시나리오를 외부 API로 유출하지 않는지 검증합니다.
class ExternalPaymentRequestTest {

    @Test
    void containsOnlyProductionPaymentFields() {
        PaymentCommand command = new PaymentCommand(
                100L,
                "user-1",
                new BigDecimal("15000.00"),
                PaymentScenario.TIMEOUT
        );

        ExternalPaymentRequest request = ExternalPaymentRequest.from(command);

        assertThat(request.orderId()).isEqualTo(100L);
        assertThat(request.userId()).isEqualTo("user-1");
        assertThat(request.amount()).isEqualByComparingTo("15000.00");
    }
}
