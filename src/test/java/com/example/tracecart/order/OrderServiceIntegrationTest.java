package com.example.tracecart.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tracecart.order.api.CreateOrderRequest;
import com.example.tracecart.order.api.OrderResponse;
import com.example.tracecart.order.application.OrderService;
import com.example.tracecart.order.domain.OrderStatus;
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

// 실제 Spring 컨테이너와 H2, JPA를 함께 띄우는 서비스 통합 테스트입니다.
@SpringBootTest
// application-test.yml과 !prod FakePaymentClient를 활성화합니다.
@ActiveProfiles("test")
class OrderServiceIntegrationTest {

    // 테스트할 실제 주문 서비스 빈입니다.
    @Autowired OrderService orderService;
    // 상품 테스트 데이터 준비와 결과 확인에 사용할 저장소입니다.
    @Autowired ProductRepository productRepository;
    // 테스트 간 주문 데이터 정리에 사용할 저장소입니다.
    @Autowired PurchaseOrderRepository orderRepository;
    // test 프로파일이 선택한 구현체 타입을 확인할 인터페이스 빈입니다.
    @Autowired PaymentClient paymentClient;

    // 각 테스트가 독립된 데이터 상태에서 시작하도록 실행 전에 정리합니다.
    @BeforeEach
    void setUp() {
        // 상품을 참조할 수 있는 주문을 먼저 삭제합니다.
        orderRepository.deleteAll();
        // 그다음 이전 테스트가 만든 상품을 삭제합니다.
        productRepository.deleteAll();
    }

    // test 프로파일에서 실제 외부 결제가 선택되지 않는지 검증합니다.
    @Test
    void testProfileUsesFakePaymentClient() {
        // 주입된 인터페이스의 실제 객체가 FakePaymentClient여야 합니다.
        assertThat(paymentClient).isInstanceOf(FakePaymentClient.class);
    }

    // 성공 결제가 주문 상태와 재고에 함께 반영되는지 검증합니다.
    @Test
    void successfulPaymentMarksOrderPaidAndDecreasesStock() {
        // Given: 가격 10,000원, 재고 5개인 상품을 H2에 저장합니다.
        Product product = productRepository.save(new Product("Keyboard", new BigDecimal("10000.00"), 5));

        // When: 상품 두 개를 SUCCESS 시나리오로 주문합니다.
        OrderResponse response = orderService.create(
                // 사용자, 상품, 수량, 결제 시나리오를 요청 DTO로 전달합니다.
                new CreateOrderRequest("user-123", product.getId(), 2, PaymentScenario.SUCCESS)
        );

        // Then: 결제가 성공해 주문 상태가 PAID여야 합니다.
        assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        // Then: 단가 10,000원 곱하기 2개의 총액은 20,000원이어야 합니다.
        assertThat(response.totalPrice()).isEqualByComparingTo("20000.00");
        // Then: 데이터베이스 상품 재고가 5개에서 3개로 줄어야 합니다.
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(3);
    }

    // 결제 실패 주문을 남기면서 차감 재고는 되돌리는지 검증합니다.
    @Test
    void failedPaymentPersistsFailureAndRestoresStock() {
        // Given: 가격 30만원, 재고 3개인 상품을 저장합니다.
        Product product = productRepository.save(new Product("Monitor", new BigDecimal("300000.00"), 3));

        // When: 상품 하나를 FAILURE 시나리오로 주문합니다.
        OrderResponse response = orderService.create(
                // Fake 결제가 PaymentException을 던지게 하는 요청입니다.
                new CreateOrderRequest("user-456", product.getId(), 1, PaymentScenario.FAILURE)
        );

        // Then: 예외가 API 밖으로 나가지 않고 실패 주문 상태로 저장돼야 합니다.
        assertThat(response.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        // Then: 결제 거절 이유가 응답에 포함돼야 합니다.
        assertThat(response.failureReason()).contains("거절");
        // Then: 먼저 차감했던 한 개가 복원되어 재고가 다시 3개여야 합니다.
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(3);
    }

    // 타임아웃도 실패 주문으로 남고 재고가 복원되는지 실제 JPA 상태까지 확인합니다.
    @Test
    void timeoutPersistsFailureAndRestoresStock() {
        Product product = productRepository.save(new Product("Mouse", new BigDecimal("50000.00"), 4));

        OrderResponse response = orderService.create(
                new CreateOrderRequest("user-789", product.getId(), 2, PaymentScenario.TIMEOUT)
        );

        assertThat(response.status()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(response.failureReason()).contains("초과");
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(4);
        assertThat(orderRepository.findById(response.id()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAYMENT_FAILED);
    }
}
