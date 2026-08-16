package com.example.tracecart.payment;

import java.math.BigDecimal;

// OrderService가 결제 구현체에 전달할 입력을 하나로 묶은 불변 객체입니다.
public record PaymentCommand(
        // 우리 시스템에서 결제가 속한 주문의 ID입니다.
        Long orderId,
        // 결제를 요청한 사용자 식별자입니다.
        String userId,
        // 승인할 총 주문 금액입니다.
        BigDecimal amount,
        // Fake 구현에서 재현할 결제 결과입니다.
        PaymentScenario scenario
) {
}
