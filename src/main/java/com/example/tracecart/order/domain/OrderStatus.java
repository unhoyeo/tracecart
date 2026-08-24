package com.example.tracecart.order.domain;

// 주문이 가질 수 있는 상태를 제한해 잘못된 문자열 상태가 저장되지 않게 합니다.
public enum OrderStatus {
    // 주문은 저장됐지만 결제 결과가 아직 반영되지 않은 상태입니다.
    PENDING,
    // 한 요청만 외부 결제를 수행하도록 주문을 선점한 상태입니다.
    PAYMENT_PROCESSING,
    // 결제가 승인되어 정상적으로 완료된 상태입니다.
    PAID,
    // 결제사가 명시적으로 거절해 결제가 완료되지 않은 상태입니다.
    PAYMENT_DECLINED,
    // 타임아웃이나 통신 장애로 결제 성공 여부를 아직 확정할 수 없는 상태입니다.
    PAYMENT_UNKNOWN
}
