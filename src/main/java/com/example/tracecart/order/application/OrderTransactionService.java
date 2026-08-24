package com.example.tracecart.order.application;

import com.example.tracecart.common.exception.BusinessException;
import com.example.tracecart.notification.OrderPaidEvent;
import com.example.tracecart.order.domain.OrderStatus;
import com.example.tracecart.order.domain.PurchaseOrder;
import com.example.tracecart.order.domain.PurchaseOrderRepository;
import com.example.tracecart.product.Product;
import com.example.tracecart.product.ProductRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

// DB 변경을 짧은 트랜잭션으로 묶어 외부 결제 대기 중에는 DB 연결과 행 잠금을 잡지 않습니다.
@Service
public class OrderTransactionService {

    private final ProductRepository productRepository;
    private final PurchaseOrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderTransactionService(
            ProductRepository productRepository,
            PurchaseOrderRepository orderRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CreateOrderResult placePendingOrder(CreateOrderCommand command) {
        Optional<PurchaseOrder> existingOrder =
                orderRepository.findByIdempotencyKey(command.idempotencyKey().value());
        if (existingOrder.isPresent()) {
            return existingResult(existingOrder.get(), command);
        }

        Product product = productRepository.findById(command.productId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "PRODUCT_NOT_FOUND",
                        "상품을 찾을 수 없습니다."
                ));
        product.decreaseStock(command.quantity().value());
        BigDecimal totalPrice = product.getPrice()
                .multiply(BigDecimal.valueOf(command.quantity().value()));
        PurchaseOrder order = orderRepository.saveAndFlush(new PurchaseOrder(
                command.idempotencyKey().value(),
                command.userId(),
                product.getId(),
                command.quantity(),
                totalPrice
        ));
        return new CreateOrderResult(OrderResult.from(order), true);
    }

    @Transactional(readOnly = true)
    public CreateOrderResult findExistingOrder(CreateOrderCommand command) {
        PurchaseOrder order = orderRepository.findByIdempotencyKey(command.idempotencyKey().value())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.CONFLICT,
                        "IDEMPOTENCY_RACE",
                        "동일한 주문 요청이 처리 중입니다. 잠시 후 다시 조회해 주세요."
                ));
        return existingResult(order, command);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PaymentAttempt> claimPayment(Long orderId) {
        PurchaseOrder order = findForUpdate(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            return Optional.empty();
        }
        order.startPayment();
        return Optional.of(new PaymentAttempt(
                order.getId(),
                order.getIdempotencyKey(),
                order.getUserId(),
                order.getTotalPrice()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderResult completePayment(Long orderId, String transactionId) {
        PurchaseOrder order = findForUpdate(orderId);
        order.markPaid(transactionId);
        eventPublisher.publishEvent(new OrderPaidEvent(order.getId(), order.getUserId()));
        // @PreUpdate가 updatedAt을 갱신한 뒤의 값을 API에 반환하도록 현재 변경을 즉시 반영합니다.
        orderRepository.flush();
        return OrderResult.from(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderResult declinePayment(Long orderId, String reason) {
        PurchaseOrder order = findForUpdate(orderId);
        Product product = productRepository.findById(order.getProductId())
                .orElseThrow(() -> new IllegalStateException("결제 거절 주문의 상품을 찾을 수 없습니다."));
        order.markPaymentDeclined(reason);
        product.restoreStock(order.getQuantity());
        orderRepository.flush();
        return OrderResult.from(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrderResult markPaymentUnknown(Long orderId, String reason) {
        PurchaseOrder order = findForUpdate(orderId);
        order.markPaymentUnknown(reason);
        orderRepository.flush();
        return OrderResult.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResult findById(Long orderId) {
        return OrderResult.from(orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND",
                        "주문을 찾을 수 없습니다."
                )));
    }

    private PurchaseOrder findForUpdate(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND",
                        "주문을 찾을 수 없습니다."
                ));
    }

    private CreateOrderResult existingResult(PurchaseOrder order, CreateOrderCommand command) {
        if (!order.hasSameRequest(command.userId(), command.productId(), command.quantity())) {
            throw new IdempotencyConflictException();
        }
        return new CreateOrderResult(OrderResult.from(order), false);
    }
}
