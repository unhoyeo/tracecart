package com.example.tracecart.order.api;

import com.example.tracecart.order.domain.OrderStatus;
import com.example.tracecart.order.application.OrderResult;
import java.math.BigDecimal;
import java.time.Instant;

// 주문 엔티티를 그대로 노출하지 않고 API에 필요한 값만 반환하는 불변 DTO입니다.
public record OrderResponse(
        // 생성된 주문의 식별자입니다.
        Long id,
        // 주문을 생성한 사용자 식별자입니다.
        String userId,
        // 주문 대상 상품 식별자입니다.
        Long productId,
        // 주문한 상품 개수입니다.
        int quantity,
        // 상품 단가와 수량을 곱한 최종 금액입니다.
        BigDecimal totalPrice,
        // 결제 처리 결과를 포함한 현재 주문 상태입니다.
        OrderStatus status,
        // 결제 승인 시 외부 결제 서버가 발급한 거래 식별자입니다.
        String transactionId,
        // 결제가 실패했다면 이유가, 성공했다면 null이 들어갑니다.
        String failureReason,
        // 주문이 데이터베이스에 처음 저장된 시각입니다.
        Instant createdAt,
        // 주문 상태가 마지막으로 변경된 시각입니다.
        Instant updatedAt
) {
    public static OrderResponse from(OrderResult order) {
        return new OrderResponse(
                order.id(),
                order.userId(),
                order.productId(),
                order.quantity(),
                order.totalPrice(),
                order.status(),
                order.transactionId(),
                order.failureReason(),
                order.createdAt(),
                order.updatedAt()
        );
    }
}
