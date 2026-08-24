package com.example.tracecart.payment;

// 결제 거절, 타임아웃, 외부 호출 실패를 OrderService에 동일한 방식으로 전달합니다.
public class PaymentException extends RuntimeException {

    private final PaymentFailureType type;

    public PaymentException(PaymentFailureType type, String message) {
        super(message);
        this.type = type;
    }

    public PaymentException(PaymentFailureType type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public PaymentFailureType type() {
        return type;
    }
}
