package com.example.tracecart.order.domain;

// 주문 도메인이 허용하지 않는 값으로 생성되거나 변경될 때 사용하는 예외입니다.
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
