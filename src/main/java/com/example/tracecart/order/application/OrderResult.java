package com.example.tracecart.order.application;

import com.example.tracecart.order.domain.OrderStatus;
import com.example.tracecart.order.domain.PurchaseOrder;
import java.math.BigDecimal;
import java.time.Instant;

// 애플리케이션 계층이 API DTO에 의존하지 않고 반환하는 주문 조회 결과입니다.
public record OrderResult(
        Long id,
        String idempotencyKey,
        String userId,
        Long productId,
        int quantity,
        BigDecimal totalPrice,
        OrderStatus status,
        String transactionId,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResult from(PurchaseOrder order) {
        return new OrderResult(
                order.getId(),
                order.getIdempotencyKey(),
                order.getUserId(),
                order.getProductId(),
                order.getQuantity(),
                order.getTotalPrice(),
                order.getStatus(),
                order.getTransactionId(),
                order.getFailureReason(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
