package com.example.tracecart.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.tracecart.common.exception.BusinessException;
import com.example.tracecart.notification.OrderPaidEvent;
import com.example.tracecart.order.api.CreateOrderRequest;
import com.example.tracecart.order.api.OrderResponse;
import com.example.tracecart.order.domain.OrderStatus;
import com.example.tracecart.order.domain.PurchaseOrder;
import com.example.tracecart.order.domain.PurchaseOrderRepository;
import com.example.tracecart.payment.PaymentClient;
import com.example.tracecart.payment.PaymentCommand;
import com.example.tracecart.payment.PaymentException;
import com.example.tracecart.payment.PaymentResult;
import com.example.tracecart.payment.PaymentScenario;
import com.example.tracecart.product.Product;
import com.example.tracecart.product.ProductRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

// Spring과 데이터베이스 없이 Mockito로 협력 객체를 대체하는 OrderService 단위 테스트입니다.
@ExtendWith(MockitoExtension.class)
class OrderServiceUnitTest {

    @Mock ProductRepository productRepository;
    @Mock PurchaseOrderRepository orderRepository;
    @Mock PaymentClient paymentClient;
    @Mock ApplicationEventPublisher eventPublisher;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                productRepository,
                orderRepository,
                paymentClient,
                eventPublisher
        );
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void successDecreasesStockMarksPaidAndRequestsNotification() {
        Product product = productWithStock(5);
        stubProductAndSavedOrder(product);
        when(paymentClient.pay(any(PaymentCommand.class))).thenAnswer(invocation -> {
            assertThat(MDC.get("orderId")).isEqualTo("100");
            return new PaymentResult("tx-100");
        });

        OrderResponse response = orderService.create(request(2, PaymentScenario.SUCCESS));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        assertThat(response.totalPrice()).isEqualByComparingTo("20000.00");
        assertThat(product.getStock()).isEqualTo(3);
        ArgumentCaptor<OrderPaidEvent> eventCaptor = ArgumentCaptor.forClass(OrderPaidEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().orderId()).isEqualTo(100L);
        assertThat(eventCaptor.getValue().userId()).isEqualTo("user-1");
        assertThat(MDC.get("orderId")).isNull();
        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void rejectedPaymentMarksFailedRestoresStockAndSkipsNotification() {
        Product product = productWithStock(5);
        stubProductAndSavedOrder(product);
        when(paymentClient.pay(any(PaymentCommand.class)))
                .thenThrow(new PaymentException("결제가 거절되었습니다."));

        OrderResponse response = orderService.create(request(2, PaymentScenario.FAILURE));

        assertThat(response.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(response.failureReason()).contains("거절");
        assertThat(product.getStock()).isEqualTo(5);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void timeoutMarksFailedRestoresStockAndSkipsNotification() {
        Product product = productWithStock(5);
        stubProductAndSavedOrder(product);
        when(paymentClient.pay(any(PaymentCommand.class)))
                .thenThrow(new PaymentException("결제 서버 응답 시간이 초과되었습니다."));

        OrderResponse response = orderService.create(request(2, PaymentScenario.TIMEOUT));

        assertThat(response.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(response.failureReason()).contains("초과");
        assertThat(product.getStock()).isEqualTo(5);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void sendsCalculatedCommandToPaymentClient() {
        Product product = productWithStock(5);
        stubProductAndSavedOrder(product);
        when(paymentClient.pay(any(PaymentCommand.class))).thenReturn(new PaymentResult("tx-100"));
        ArgumentCaptor<PaymentCommand> commandCaptor = ArgumentCaptor.forClass(PaymentCommand.class);

        orderService.create(request(3, PaymentScenario.SUCCESS));

        verify(paymentClient).pay(commandCaptor.capture());
        PaymentCommand command = commandCaptor.getValue();
        assertThat(command.orderId()).isEqualTo(100L);
        assertThat(command.userId()).isEqualTo("user-1");
        assertThat(command.amount()).isEqualByComparingTo("30000.00");
        assertThat(command.scenario()).isEqualTo(PaymentScenario.SUCCESS);
    }

    @Test
    void throwsNotFoundBeforeSavingOrPaying() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.create(request(1, PaymentScenario.SUCCESS)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.code()).isEqualTo("PRODUCT_NOT_FOUND");
                });
        verify(orderRepository, never()).saveAndFlush(any(PurchaseOrder.class));
        verifyNoInteractions(paymentClient, eventPublisher);
    }

    @Test
    void throwsConflictBeforeSavingOrPayingWhenStockIsInsufficient() {
        Product product = productWithStock(1);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.create(request(2, PaymentScenario.SUCCESS)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.code()).isEqualTo("INSUFFICIENT_STOCK");
                });
        assertThat(product.getStock()).isEqualTo(1);
        verify(orderRepository, never()).saveAndFlush(any(PurchaseOrder.class));
        verifyNoInteractions(paymentClient, eventPublisher);
    }

    @Test
    void rejectsNullRequestBeforeCallingAnyCollaborator() {
        assertThatThrownBy(() -> orderService.create(null))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.code()).isEqualTo("INVALID_REQUEST");
                });
        verifyNoInteractions(productRepository, orderRepository, paymentClient, eventPublisher);
    }

    @Test
    void rejectsMissingScenarioBeforeDecreasingStock() {
        CreateOrderRequest invalidRequest = new CreateOrderRequest("user-1", 1L, 1, null);

        assertThatThrownBy(() -> orderService.create(invalidRequest))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code()).isEqualTo("INVALID_REQUEST"));
        verifyNoInteractions(productRepository, orderRepository, paymentClient, eventPublisher);
    }

    private Product productWithStock(int stock) {
        return new Product("Keyboard", new BigDecimal("10000.00"), stock);
    }

    private CreateOrderRequest request(int quantity, PaymentScenario scenario) {
        return new CreateOrderRequest("user-1", 1L, quantity, scenario);
    }

    private void stubProductAndSavedOrder(Product product) {
        ReflectionTestUtils.setField(product, "id", 1L);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.saveAndFlush(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 100L);
            return order;
        });
    }
}
