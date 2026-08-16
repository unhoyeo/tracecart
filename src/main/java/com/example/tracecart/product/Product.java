package com.example.tracecart.product;

import com.example.tracecart.common.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // 돈 계산 오차를 피하려고 실수형 대신 BigDecimal을 사용하며 소수 둘째 자리까지 저장합니다.
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    // JPA가 데이터베이스 값을 채워 객체를 만들 때 사용하는 기본 생성자입니다.
    protected Product() {
    }

    // 애플리케이션이 새 상품을 만들 때 사용하는 생성자입니다.
    public Product(String name, BigDecimal price, int stock) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("상품 이름은 비어 있을 수 없습니다.");
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("상품 가격은 0보다 커야 합니다.");
        }
        if (stock < 0) {
            throw new IllegalArgumentException("상품 재고는 음수일 수 없습니다.");
        }
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    // 주문 수량만큼 재고를 차감하는 도메인 규칙입니다.
    public void decreaseStock(int quantity) {
        // Controller 이외의 진입점에서도 음수 수량이 재고를 증가시키지 못하게 도메인이 방어합니다.
        if (quantity <= 0) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_QUANTITY",
                    "주문 수량은 1 이상이어야 합니다."
            );
        }
        if (stock < quantity) {
            throw new BusinessException(
                    // 현재 자원 상태와 요청이 충돌했으므로 409를 사용합니다.
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_STOCK",
                    "재고가 부족합니다. 현재 재고: " + stock
            );
        }
        stock -= quantity;
    }

    // 결제가 실패했을 때 먼저 차감했던 재고를 원상 복구합니다.
    public void restoreStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("복원 수량은 1 이상이어야 합니다.");
        }
        stock += quantity;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }
}
