package com.example.tracecart.notification;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.example.tracecart.order.api.CreateOrderRequest;
import com.example.tracecart.order.api.OrderResponse;
import com.example.tracecart.order.application.OrderService;
import com.example.tracecart.order.domain.PurchaseOrderRepository;
import com.example.tracecart.payment.PaymentScenario;
import com.example.tracecart.product.Product;
import com.example.tracecart.product.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

// 실제 트랜잭션과 비동기 리스너를 사용해 알림이 커밋 뒤에만 실행되는지 검증합니다.
@SpringBootTest
@ActiveProfiles("test")
class OrderPaidAfterCommitIntegrationTest {

    @Autowired OrderService orderService;
    @Autowired ProductRepository productRepository;
    @Autowired PurchaseOrderRepository orderRepository;
    @Autowired TransactionTemplate transactionTemplate;

    // 실제 로그 알림 대신 호출 시점과 횟수를 관찰할 mock 빈으로 교체합니다.
    @MockitoBean NotificationService notificationService;

    private Long productId;

    @BeforeEach
    void setUp() {
        reset(notificationService);
        orderRepository.deleteAll();
        productRepository.deleteAll();
        productId = productRepository.save(
                new Product("Commit Keyboard", new BigDecimal("10000.00"), 2)
        ).getId();
    }

    @Test
    void sendsNotificationAfterTransactionCommits() {
        OrderResponse response = transactionTemplate.execute(status -> orderService.create(request()));

        verify(notificationService, timeout(2_000))
                .sendOrderCompleted(response.id(), "commit-user");
    }

    @Test
    void doesNotSendNotificationWhenTransactionRollsBack() {
        transactionTemplate.executeWithoutResult(status -> {
            orderService.create(request());
            status.setRollbackOnly();
        });

        verify(notificationService, after(400).never())
                .sendOrderCompleted(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private CreateOrderRequest request() {
        return new CreateOrderRequest("commit-user", productId, 1, PaymentScenario.SUCCESS);
    }
}
