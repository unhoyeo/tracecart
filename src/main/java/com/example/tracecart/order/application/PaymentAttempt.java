package com.example.tracecart.order.application;

import java.math.BigDecimal;

// 결제 선점에 성공한 요청만 외부 결제에 전달할 값입니다.
public record PaymentAttempt(
        Long orderId,
        String idempotencyKey,
        String userId,
        BigDecimal amount
) {
}
