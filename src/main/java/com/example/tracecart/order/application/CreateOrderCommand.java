package com.example.tracecart.order.application;

import com.example.tracecart.order.domain.IdempotencyKey;
import com.example.tracecart.order.domain.OrderQuantity;
import com.example.tracecart.order.domain.OrderUserId;
import com.example.tracecart.payment.PaymentScenario;
import com.example.tracecart.order.domain.InvalidOrderException;

// HTTP DTO와 분리해 주문 유스케이스가 받는 타입 안전한 입력입니다.
public record CreateOrderCommand(
        IdempotencyKey idempotencyKey,
        OrderUserId userId,
        Long productId,
        OrderQuantity quantity,
        PaymentScenario simulationScenario
) {
    public CreateOrderCommand {
        if (idempotencyKey == null || userId == null || quantity == null || simulationScenario == null) {
            throw new InvalidOrderException("주문 명령의 필수값이 누락되었습니다.");
        }
        if (productId == null || productId <= 0) {
            throw new InvalidOrderException("상품 ID는 양수여야 합니다.");
        }
    }
}
