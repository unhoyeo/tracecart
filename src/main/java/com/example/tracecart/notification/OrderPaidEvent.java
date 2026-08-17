package com.example.tracecart.notification;

// 결제 완료 트랜잭션이 커밋된 뒤 알림에 필요한 최소 데이터만 전달하는 불변 이벤트입니다.
public record OrderPaidEvent(
        // 커밋된 주문을 식별할 ID입니다.
        Long orderId,
        // 알림 수신자를 식별할 사용자 ID입니다.
        String userId
) {
}
