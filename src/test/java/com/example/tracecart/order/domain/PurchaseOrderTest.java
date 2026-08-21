package com.example.tracecart.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

// JPA 없이 주문 상태 전이 규칙만 빠르게 확인하는 순수 단위 테스트입니다.
class PurchaseOrderTest {

    @Test
    void newOrderStartsPending() {
        PurchaseOrder order = new PurchaseOrder("user-1", 10L, 2, new BigDecimal("20000.00"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getFailureReason()).isNull();
    }

    @Test
    void marksOrderPaidAndClearsPreviousFailureReason() {
        PurchaseOrder order = new PurchaseOrder("user-1", 10L, 2, new BigDecimal("20000.00"));
        order.markPaymentFailed("temporary failure");

        order.markPaid();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getFailureReason()).isNull();
    }

    @Test
    void recordsPaymentFailureReason() {
        PurchaseOrder order = new PurchaseOrder("user-1", 10L, 2, new BigDecimal("20000.00"));

        order.markPaymentFailed("결제 서버 응답 시간이 초과되었습니다.");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.getFailureReason()).contains("초과");
    }

    @Test
    void rejectsInvalidValuesAtConstructionTime() {
        assertThatThrownBy(() -> new PurchaseOrder(" ", 10L, 1, new BigDecimal("10000.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자");
        assertThatThrownBy(() -> new PurchaseOrder("user-1", 0L, 1, new BigDecimal("10000.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("상품 ID");
        assertThatThrownBy(() -> new PurchaseOrder("user-1", 10L, 0, new BigDecimal("10000.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수량");
        assertThatThrownBy(() -> new PurchaseOrder("user-1", 10L, 1, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액");
    }
}
