package com.example.tracecart.payment;

// 결제 미완료 원인에 따라 주문 상태와 재고 복원 여부를 다르게 결정하기 위한 분류입니다.
public enum PaymentFailureType {
    DECLINED,
    TIMEOUT,
    UNAVAILABLE,
    INVALID_RESPONSE,
    INTERRUPTED
}
