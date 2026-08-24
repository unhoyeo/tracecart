package com.example.tracecart.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.tracecart.order.domain.IdempotencyKey;
import com.example.tracecart.order.domain.OrderQuantity;
import com.example.tracecart.order.domain.OrderStatus;
import com.example.tracecart.order.domain.OrderUserId;
import com.example.tracecart.payment.PaymentClient;
import com.example.tracecart.payment.PaymentCommand;
import com.example.tracecart.payment.PaymentException;
import com.example.tracecart.payment.PaymentFailureType;
import com.example.tracecart.payment.PaymentResult;
import com.example.tracecart.payment.PaymentScenario;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;

// DB 트랜잭션 단계를 mock으로 바꿔 외부 결제 조율 분기만 검증합니다.
@ExtendWith(MockitoExtension.class)
class OrderServiceUnitTest {

    @Mock OrderTransactionService transactionService;
    @Mock PaymentClient paymentClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(transactionService, paymentClient);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void successClaimsPaymentAndCompletesOrder() {
        stubNewPlacement();
        when(paymentClient.pay(any(PaymentCommand.class))).thenAnswer(invocation -> {
            assertThat(MDC.get("orderId")).isEqualTo("100");
            return new PaymentResult("tx-100");
        });
        when(transactionService.completePayment(100L, "tx-100"))
                .thenReturn(order(OrderStatus.PAID, "tx-100", null));

        CreateOrderResult result = orderService.create(command(PaymentScenario.SUCCESS));

        assertThat(result.order().status()).isEqualTo(OrderStatus.PAID);
        assertThat(result.newlyCreated()).isTrue();
        assertThat(MDC.get("orderId")).isNull();
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void explicitDeclineRestoresThroughDeclineTransaction() {
        stubNewPlacement();
        when(paymentClient.pay(any(PaymentCommand.class))).thenThrow(new PaymentException(
                PaymentFailureType.DECLINED,
                "결제가 거절되었습니다."
        ));
        when(transactionService.declinePayment(100L, "결제가 거절되었습니다."))
                .thenReturn(order(OrderStatus.PAYMENT_DECLINED, null, "결제가 거절되었습니다."));

        CreateOrderResult result = orderService.create(command(PaymentScenario.FAILURE));

        assertThat(result.order().status()).isEqualTo(OrderStatus.PAYMENT_DECLINED);
        verify(transactionService, never()).markPaymentUnknown(any(), any());
    }

    @Test
    void timeoutDoesNotDeclineOrRestoreStock() {
        stubNewPlacement();
        when(paymentClient.pay(any(PaymentCommand.class))).thenThrow(new PaymentException(
                PaymentFailureType.TIMEOUT,
                "결제 결과를 확인 중입니다."
        ));
        when(transactionService.markPaymentUnknown(100L, "결제 결과를 확인 중입니다."))
                .thenReturn(order(OrderStatus.PAYMENT_UNKNOWN, null, "결제 결과를 확인 중입니다."));

        CreateOrderResult result = orderService.create(command(PaymentScenario.TIMEOUT));

        assertThat(result.order().status()).isEqualTo(OrderStatus.PAYMENT_UNKNOWN);
        verify(transactionService, never()).declinePayment(any(), any());
    }

    @Test
    void finalIdempotentReplayDoesNotPayAgain() {
        when(transactionService.placePendingOrder(any()))
                .thenReturn(new CreateOrderResult(order(OrderStatus.PAID, "tx-old", null), false));

        CreateOrderResult result = orderService.create(command(PaymentScenario.SUCCESS));

        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.order().transactionId()).isEqualTo("tx-old");
        verifyNoInteractions(paymentClient);
        verify(transactionService, never()).claimPayment(any());
    }

    @Test
    void requestThatLosesPaymentClaimReturnsCurrentState() {
        stubPlacementOnly();
        when(transactionService.claimPayment(100L)).thenReturn(Optional.empty());
        when(transactionService.findById(100L))
                .thenReturn(order(OrderStatus.PAYMENT_PROCESSING, null, null));

        CreateOrderResult result = orderService.create(command(PaymentScenario.SUCCESS));

        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.order().status()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);
        verifyNoInteractions(paymentClient);
    }

    @Test
    void uniqueKeyRaceLoadsWinningOrder() {
        when(transactionService.placePendingOrder(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(transactionService.findExistingOrder(any()))
                .thenReturn(new CreateOrderResult(order(OrderStatus.PAID, "tx-winner", null), false));

        CreateOrderResult result = orderService.create(command(PaymentScenario.SUCCESS));

        assertThat(result.order().transactionId()).isEqualTo("tx-winner");
        verifyNoInteractions(paymentClient);
    }

    @Test
    void unexpectedClientErrorMarksUnknownAndRethrows() {
        stubNewPlacement();
        when(paymentClient.pay(any())).thenThrow(new IllegalStateException("bug"));

        assertThatThrownBy(() -> orderService.create(command(PaymentScenario.SUCCESS)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("bug");
        verify(transactionService).markPaymentUnknown(
                100L,
                "예상하지 못한 결제 오류로 결과를 확인 중입니다."
        );
    }

    private void stubNewPlacement() {
        stubPlacementOnly();
        when(transactionService.claimPayment(100L)).thenReturn(Optional.of(
                new PaymentAttempt(100L, "idem-unit-0001", "user-1", new BigDecimal("20000.00"))
        ));
    }

    private void stubPlacementOnly() {
        when(transactionService.placePendingOrder(any()))
                .thenReturn(new CreateOrderResult(order(OrderStatus.PENDING, null, null), true));
    }

    private CreateOrderCommand command(PaymentScenario scenario) {
        return new CreateOrderCommand(
                new IdempotencyKey("idem-unit-0001"),
                new OrderUserId("user-1"),
                1L,
                new OrderQuantity(2),
                scenario
        );
    }

    private OrderResult order(OrderStatus status, String transactionId, String failureReason) {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        return new OrderResult(
                100L,
                "idem-unit-0001",
                "user-1",
                1L,
                2,
                new BigDecimal("20000.00"),
                status,
                transactionId,
                failureReason,
                now,
                now
        );
    }
}
