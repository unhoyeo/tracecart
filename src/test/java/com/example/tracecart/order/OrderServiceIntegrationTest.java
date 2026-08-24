package com.example.tracecart.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tracecart.order.application.CreateOrderCommand;
import com.example.tracecart.order.application.CreateOrderResult;
import com.example.tracecart.order.application.IdempotencyConflictException;
import com.example.tracecart.order.application.OrderService;
import com.example.tracecart.order.domain.IdempotencyKey;
import com.example.tracecart.order.domain.OrderQuantity;
import com.example.tracecart.order.domain.OrderStatus;
import com.example.tracecart.order.domain.OrderUserId;
import com.example.tracecart.order.domain.PurchaseOrderRepository;
import com.example.tracecart.payment.FakePaymentClient;
import com.example.tracecart.payment.PaymentClient;
import com.example.tracecart.payment.PaymentScenario;
import com.example.tracecart.product.Product;
import com.example.tracecart.product.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 실제 Spring, Flyway, H2, JPA를 사용해 분리된 주문 트랜잭션 전체를 검증합니다.
@SpringBootTest
@ActiveProfiles("test")
class OrderServiceIntegrationTest {

    @Autowired OrderService orderService;
    @Autowired ProductRepository productRepository;
    @Autowired PurchaseOrderRepository orderRepository;
    @Autowired PaymentClient paymentClient;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void testProfileUsesFakePaymentClient() {
        assertThat(paymentClient).isInstanceOf(FakePaymentClient.class);
    }

    @Test
    void successfulPaymentCommitsPaidOrderAndDecreasesStock() {
        Product product = saveProduct(5);

        CreateOrderResult result = orderService.create(
                command("idem-success-0001", product.getId(), 2, PaymentScenario.SUCCESS)
        );

        assertThat(result.order().status()).isEqualTo(OrderStatus.PAID);
        assertThat(result.order().transactionId()).startsWith("fake-");
        assertThat(result.order().totalPrice()).isEqualByComparingTo("20000.00");
        assertThat(currentStock(product)).isEqualTo(3);
    }

    @Test
    void declinedPaymentPersistsDeclineAndRestoresStock() {
        Product product = saveProduct(3);

        CreateOrderResult result = orderService.create(
                command("idem-decline-0001", product.getId(), 1, PaymentScenario.FAILURE)
        );

        assertThat(result.order().status()).isEqualTo(OrderStatus.PAYMENT_DECLINED);
        assertThat(result.order().failureReason()).contains("거절");
        assertThat(currentStock(product)).isEqualTo(3);
    }

    @Test
    void timeoutKeepsReservationAndPersistsUnknownStatus() {
        Product product = saveProduct(4);

        CreateOrderResult result = orderService.create(
                command("idem-timeout-0001", product.getId(), 2, PaymentScenario.TIMEOUT)
        );

        assertThat(result.order().status()).isEqualTo(OrderStatus.PAYMENT_UNKNOWN);
        assertThat(result.order().failureReason()).contains("확인");
        assertThat(currentStock(product)).isEqualTo(2);
        assertThat(orderRepository.findById(result.order().id()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAYMENT_UNKNOWN);
    }

    @Test
    void sameIdempotencyKeyReturnsExistingOrderWithoutDecreasingStockAgain() {
        Product product = saveProduct(5);
        CreateOrderCommand command =
                command("idem-replay-0001", product.getId(), 2, PaymentScenario.SUCCESS);

        CreateOrderResult first = orderService.create(command);
        CreateOrderResult replay = orderService.create(command);

        assertThat(replay.newlyCreated()).isFalse();
        assertThat(replay.order().id()).isEqualTo(first.order().id());
        assertThat(replay.order().transactionId()).isEqualTo(first.order().transactionId());
        assertThat(currentStock(product)).isEqualTo(3);
        assertThat(orderRepository.count()).isOne();
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadIsRejected() {
        Product product = saveProduct(5);
        orderService.create(command("idem-conflict-0001", product.getId(), 1, PaymentScenario.SUCCESS));

        assertThatThrownBy(() -> orderService.create(
                command("idem-conflict-0001", product.getId(), 2, PaymentScenario.SUCCESS)
        )).isInstanceOf(IdempotencyConflictException.class);
        assertThat(currentStock(product)).isEqualTo(4);
    }

    private Product saveProduct(int stock) {
        return productRepository.save(new Product("Keyboard", new BigDecimal("10000.00"), stock));
    }

    private int currentStock(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getStock();
    }

    private CreateOrderCommand command(
            String idempotencyKey,
            Long productId,
            int quantity,
            PaymentScenario scenario
    ) {
        return new CreateOrderCommand(
                new IdempotencyKey(idempotencyKey),
                new OrderUserId("user-123"),
                productId,
                new OrderQuantity(quantity),
                scenario
        );
    }
}
