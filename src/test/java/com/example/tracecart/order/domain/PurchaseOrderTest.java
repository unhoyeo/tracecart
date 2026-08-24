package com.example.tracecart.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

// JPA 없이 주문 불변식과 허용된 상태 전이만 검증하는 순수 단위 테스트입니다.
class PurchaseOrderTest {

    @Test
    void newOrderStartsPending() {
        PurchaseOrder order = order();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTransactionId()).isNull();
        assertThat(order.getFailureReason()).isNull();
    }

    @Test
    void paymentMustBeClaimedBeforeItCanBeCompleted() {
        PurchaseOrder order = order();

        assertThatThrownBy(() -> order.markPaid("tx-100"))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("결제 처리");
    }

    @Test
    void claimedPaymentCanBeApproved() {
        PurchaseOrder order = order();
        order.startPayment();

        order.markPaid("tx-100");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getTransactionId()).isEqualTo("tx-100");
        assertThat(order.getFailureReason()).isNull();
    }

    @Test
    void declineAndUnknownAreDifferentStates() {
        PurchaseOrder declined = order();
        declined.startPayment();
        declined.markPaymentDeclined("결제가 거절되었습니다.");

        PurchaseOrder unknown = order("idem-unknown-0001");
        unknown.startPayment();
        unknown.markPaymentUnknown("결제 결과를 확인 중입니다.");

        assertThat(declined.getStatus()).isEqualTo(OrderStatus.PAYMENT_DECLINED);
        assertThat(unknown.getStatus()).isEqualTo(OrderStatus.PAYMENT_UNKNOWN);
    }

    @Test
    void unknownPaymentCanLaterBeReconciledAsPaid() {
        PurchaseOrder order = order();
        order.startPayment();
        order.markPaymentUnknown("응답 시간 초과");

        order.markPaid("reconciled-tx");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getFailureReason()).isNull();
    }

    @Test
    void terminalOrderCannotStartAnotherPayment() {
        PurchaseOrder order = order();
        order.startPayment();
        order.markPaid("tx-100");

        assertThatThrownBy(order::startPayment)
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void validatesValueObjectsAndMoneyScale() {
        assertThatThrownBy(() -> new OrderUserId(" "))
                .isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> new OrderQuantity(0))
                .isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> new OrderQuantity(101))
                .isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> new IdempotencyKey("short"))
                .isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> new PurchaseOrder(
                "idem-order-0001",
                new OrderUserId("user-1"),
                10L,
                new OrderQuantity(1),
                new BigDecimal("1.001")
        )).isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void detectsDifferentRequestUsingSameIdempotencyKey() {
        PurchaseOrder order = order();

        assertThat(order.hasSameRequest(new OrderUserId("user-1"), 10L, new OrderQuantity(2))).isTrue();
        assertThat(order.hasSameRequest(new OrderUserId("other"), 10L, new OrderQuantity(2))).isFalse();
        assertThat(order.hasSameRequest(new OrderUserId("user-1"), 11L, new OrderQuantity(2))).isFalse();
    }

    private PurchaseOrder order() {
        return order("idem-order-0001");
    }

    private PurchaseOrder order(String idempotencyKey) {
        return new PurchaseOrder(
                idempotencyKey,
                new OrderUserId("user-1"),
                10L,
                new OrderQuantity(2),
                new BigDecimal("20000.00")
        );
    }
}
