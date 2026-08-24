package com.example.tracecart.order.domain;

// 한 주문에서 허용하는 상품 수량 범위를 표현하는 값 객체입니다.
public record OrderQuantity(int value) {

    public static final int MIN = 1;
    public static final int MAX = 100;

    public OrderQuantity {
        if (value < MIN || value > MAX) {
            throw new InvalidOrderException("주문 수량은 1개 이상 100개 이하여야 합니다.");
        }
    }
}
