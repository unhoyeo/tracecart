package com.example.tracecart.payment;

// 결제 거절, 타임아웃, 외부 호출 실패를 OrderService에 동일한 방식으로 전달합니다.
public class PaymentException extends RuntimeException {

    // 결제 실패 이유를 메시지로 받는 생성자입니다.
    public PaymentException(String message) {
        // RuntimeException에 이유를 저장해 getMessage()로 읽을 수 있게 합니다.
        super(message);
    }
}
