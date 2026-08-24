package com.example.tracecart.product;

// 상품이 요청 수량만큼 재고를 제공할 수 없을 때 발생하는 도메인 예외입니다.
public class InsufficientStockException extends RuntimeException {

    private final int availableStock;

    public InsufficientStockException(int availableStock) {
        super("재고가 부족합니다. 현재 재고: " + availableStock);
        this.availableStock = availableStock;
    }

    public int availableStock() {
        return availableStock;
    }
}
