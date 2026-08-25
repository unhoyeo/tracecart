package com.example.tracecart.order.application;

import com.example.tracecart.common.exception.BusinessException;
import com.example.tracecart.notification.OrderPaidEvent;
import com.example.tracecart.order.api.CreateOrderRequest;
import com.example.tracecart.order.api.OrderResponse;
import com.example.tracecart.order.domain.PurchaseOrder;
import com.example.tracecart.order.domain.PurchaseOrderRepository;
import com.example.tracecart.payment.PaymentClient;
import com.example.tracecart.payment.PaymentCommand;
import com.example.tracecart.payment.PaymentException;
import com.example.tracecart.payment.PaymentResult;
import com.example.tracecart.product.Product;
import com.example.tracecart.product.ProductRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final ProductRepository productRepository;
    private final PurchaseOrderRepository orderRepository;
    private final PaymentClient paymentClient;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(
            ProductRepository productRepository,
            PurchaseOrderRepository orderRepository,
            PaymentClient paymentClient,
            ApplicationEventPublisher eventPublisher
    ) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        // BEFORE: 값을 넣기만 하고 메서드가 끝난 뒤 제거하거나 이전 값으로 복원하지 않습니다.
        MDC.put("userId", request.userId());
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "PRODUCT_NOT_FOUND",
                        "상품을 찾을 수 없습니다."
                ));

        product.decreaseStock(request.quantity());
        BigDecimal totalPrice = product.getPrice().multiply(BigDecimal.valueOf(request.quantity()));
        // 아직 결제 전인 PENDING 주문을 만들고 즉시 데이터베이스에 INSERT합니다.
        PurchaseOrder order = orderRepository.saveAndFlush(
                new PurchaseOrder(request.userId(), product.getId(), request.quantity(), totalPrice)
        );

        // BEFORE: 주문 처리 뒤에도 orderId가 같은 요청 스레드의 MDC에 남습니다.
        MDC.put("orderId", String.valueOf(order.getId()));
        log.info("Order created: productId={}, quantity={}, totalPrice={}",
                product.getId(), request.quantity(), totalPrice);
        processPayment(order, product, request);
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long orderId) {
        // BEFORE: 조회가 끝난 뒤에도 orderId를 제거하지 않습니다.
        MDC.put("orderId", String.valueOf(orderId));
        PurchaseOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND",
                        "주문을 찾을 수 없습니다."
                ));
        log.debug("Order found");
        return OrderResponse.from(order);
    }

    // create 메서드에서 결제 결과에 따라 주문과 재고를 바꾸는 내부 단계입니다.
    private void processPayment(PurchaseOrder order, Product product, CreateOrderRequest request) {
        // 결제 구현체가 필요로 하는 값만 모아 불변 명령 객체를 만듭니다.
        PaymentCommand command = new PaymentCommand(
                order.getId(),
                order.getUserId(),
                order.getTotalPrice(),
                request.paymentScenario()
        );

        try {
            // local/dev에서는 FakePaymentClient, prod에서는 ExternalPaymentClient가 실행됩니다.
            PaymentResult result = paymentClient.pay(command);
            // 예외 없이 승인 결과가 오면 주문 상태를 PAID로 변경합니다.
            order.markPaid();
            // 결제 서버가 발급한 거래 ID를 승인 로그에 남깁니다.
            log.info("Payment approved: transactionId={}", result.transactionId());
            // 이벤트는 현재 트랜잭션이 실제 커밋된 후에만 비동기 알림 리스너가 처리합니다.
            eventPublisher.publishEvent(new OrderPaidEvent(order.getId(), order.getUserId()));
        } catch (PaymentException exception) {
            // 결제가 완료되지 않았으므로 먼저 차감한 상품 재고를 복원합니다.
            product.restoreStock(order.getQuantity());
            // 예외를 다시 던지지 않고 실패 상태와 이유를 주문에 기록합니다.
            order.markPaymentFailed(exception.getMessage());
            log.warn("Payment failed: reason={}", exception.getMessage());
        }
    }
}
