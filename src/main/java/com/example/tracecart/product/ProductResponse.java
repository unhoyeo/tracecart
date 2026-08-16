package com.example.tracecart.product;

import java.math.BigDecimal;

// 엔티티를 직접 노출하지 않고 API에 필요한 상품 정보만 전달하는 DTO입니다.
public record ProductResponse(
        // 상품 기본 키입니다.
        Long id,
        // 화면에 표시할 상품 이름입니다.
        String name,
        // 정확한 십진수 상품 가격입니다.
        BigDecimal price,
        // 현재 주문 가능한 재고 수량입니다.
        int stock
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getPrice(), product.getStock());
    }
}
