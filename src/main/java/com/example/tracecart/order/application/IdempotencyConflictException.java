package com.example.tracecart.order.application;

// 같은 멱등성 키가 서로 다른 주문 내용에 재사용됐을 때 발생합니다.
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("같은 Idempotency-Key를 다른 주문 내용에 재사용할 수 없습니다.");
    }
}
