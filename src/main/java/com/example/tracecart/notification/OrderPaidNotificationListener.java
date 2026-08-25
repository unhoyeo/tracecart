package com.example.tracecart.notification;

import com.example.tracecart.common.logging.MdcScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 결제 완료 이벤트를 DB 커밋 이후에만 비동기로 알림 서비스에 전달합니다.
@Component
public class OrderPaidNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidNotificationListener.class);
    private final NotificationService notificationService;

    public OrderPaidNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // 커밋 실패나 롤백 시에는 호출되지 않으며, 호출될 때는 전용 알림 스레드를 사용합니다.
    @Async("applicationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderPaidEvent event) {
        // MdcScope로 이벤트 값을 넣기 전 로그이므로 TaskDecorator가 전달한 MDC 상태를 관찰할 수 있습니다.
        log.info("Async order notification received");
        // 이벤트 처리 로그에도 주문자와 주문 ID가 보이도록 비동기 스레드 MDC 범위를 복원합니다.
        try (MdcScope userScope = MdcScope.with("userId", event.userId());
             MdcScope orderScope = MdcScope.with("orderId", event.orderId())) {
            notificationService.sendOrderCompleted(event.orderId(), event.userId());
        }
    }
}
