package com.example.tracecart.order.domain;

// 주문이 가질 수 있는 상태를 제한해 잘못된 문자열 상태가 저장되지 않게 합니다.
public enum OrderStatus {
    // 주문은 저장됐지만 결제 결과가 아직 반영되지 않은 상태입니다.
    PENDING,
    // 결제가 승인되어 정상적으로 완료된 상태입니다.
    PAID,
    // 결제 거절이나 타임아웃 때문에 완료되지 못한 상태입니다.
    PAYMENT_FAILED
}
