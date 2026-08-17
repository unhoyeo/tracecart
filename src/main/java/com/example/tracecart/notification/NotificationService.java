package com.example.tracecart.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// 주문 완료 알림이라는 별도 업무 역할을 담당하는 Spring 서비스입니다.
@Service
public class NotificationService {

    // 데모에서는 실제 이메일 대신 알림 전송을 로그로 관찰합니다.
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    // 비동기 전환과 커밋 시점 제어는 OrderPaidNotificationListener가 담당합니다.
    public void sendOrderCompleted(Long orderId, String userId) {
        // TaskDecorator가 복사한 traceId와 리스너가 넣은 orderId/userId를 비동기 로그에서 확인합니다.
        log.info("Order completion notification sent: orderId={}, recipient={}", orderId, userId);
    }
}
