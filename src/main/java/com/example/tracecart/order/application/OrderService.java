package com.example.tracecart.order.application;

import com.example.tracecart.common.logging.MdcScope;
import com.example.tracecart.order.domain.OrderStatus;
import com.example.tracecart.payment.PaymentClient;
import com.example.tracecart.payment.PaymentCommand;
import com.example.tracecart.payment.PaymentException;
import com.example.tracecart.payment.PaymentFailureType;
import com.example.tracecart.payment.PaymentResult;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

// 트랜잭션 단계와 외부 결제 호출을 조율하는 주문 유스케이스 서비스입니다.
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final OrderTransactionService transactionService;
    private final PaymentClient paymentClient;

    public OrderService(OrderTransactionService transactionService, PaymentClient paymentClient) {
        this.transactionService = transactionService;
        this.paymentClient = paymentClient;
    }

    // 이 메서드에는 @Transactional을 붙이지 않아 외부 HTTP 대기 중 DB 트랜잭션을 유지하지 않습니다.
    public CreateOrderResult create(CreateOrderCommand command) {
        try (MdcScope ignored = MdcScope.with("userId", command.userId().value())) {
            CreateOrderResult placement = placeIdempotently(command);
            Long orderId = placement.order().id();

            try (MdcScope ignored2 = MdcScope.with("orderId", orderId)) {
                if (placement.order().status() != OrderStatus.PENDING) {
                    log.info("Idempotent order replayed: status={}", placement.order().status());
                    return placement;
                }

                Optional<PaymentAttempt> claimed = transactionService.claimPayment(orderId);
                if (claimed.isEmpty()) {
                    return new CreateOrderResult(transactionService.findById(orderId), false);
                }

                log.info("Order created and payment claimed: productId={}, quantity={}, totalPrice={}",
                        command.productId(), command.quantity().value(), claimed.get().amount());
                OrderResult processed = processPayment(claimed.get(), command);
                return new CreateOrderResult(processed, placement.newlyCreated());
            }
        }
    }

    public OrderResult findById(Long orderId) {
        try (MdcScope ignored = MdcScope.with("orderId", orderId)) {
            OrderResult order = transactionService.findById(orderId);
            log.debug("Order found");
            return order;
        }
    }

    private CreateOrderResult placeIdempotently(CreateOrderCommand command) {
        try {
            return transactionService.placePendingOrder(command);
        } catch (DataIntegrityViolationException exception) {
            // 동시에 같은 멱등성 키를 INSERT한 요청은 승자 주문을 읽어 같은 결과를 재사용합니다.
            return transactionService.findExistingOrder(command);
        }
    }

    private OrderResult processPayment(PaymentAttempt attempt, CreateOrderCommand command) {
        PaymentCommand paymentCommand = new PaymentCommand(
                attempt.orderId(),
                attempt.userId(),
                attempt.amount(),
                attempt.idempotencyKey(),
                command.simulationScenario()
        );
        try {
            PaymentResult result = paymentClient.pay(paymentCommand);
            OrderResult paidOrder = transactionService.completePayment(
                    attempt.orderId(),
                    result.transactionId()
            );
            log.info("Payment approved: transactionId={}", result.transactionId());
            return paidOrder;
        } catch (PaymentException exception) {
            if (exception.type() == PaymentFailureType.DECLINED) {
                OrderResult declinedOrder = transactionService.declinePayment(
                        attempt.orderId(),
                        exception.getMessage()
                );
                log.warn("Payment declined: reason={}", exception.getMessage());
                return declinedOrder;
            }

            // 타임아웃과 통신 장애는 실제 승인 가능성이 있으므로 실패로 단정하거나 재고를 복원하지 않습니다.
            OrderResult unknownOrder = transactionService.markPaymentUnknown(
                    attempt.orderId(),
                    exception.getMessage()
            );
            log.warn("Payment result unknown: type={}, reason={}", exception.type(), exception.getMessage());
            return unknownOrder;
        } catch (RuntimeException exception) {
            // 예상하지 못한 결제 구현 오류도 주문을 처리 중 상태에 방치하지 않고 확인 필요 상태로 남깁니다.
            transactionService.markPaymentUnknown(
                    attempt.orderId(),
                    "예상하지 못한 결제 오류로 결과를 확인 중입니다."
            );
            throw exception;
        }
    }
}
