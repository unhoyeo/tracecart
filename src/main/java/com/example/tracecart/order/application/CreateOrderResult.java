package com.example.tracecart.order.application;

// 멱등 재요청인지 구분해 Controller가 201과 재조회 응답을 올바르게 선택하게 합니다.
public record CreateOrderResult(OrderResult order, boolean newlyCreated) {
}
