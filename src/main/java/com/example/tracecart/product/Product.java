package com.example.tracecart.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;

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

    // 동시에 같은 상품을 수정했을 때 재고 덮어쓰기를 감지하는 낙관적 잠금 버전입니다.
    @Version
    private long version;

    // JPA가 데이터베이스 값을 채워 객체를 만들 때 사용하는 기본 생성자입니다.
    protected Product() {
    }

    // 애플리케이션이 새 상품을 만들 때 사용하는 생성자입니다.
    public Product(String name, BigDecimal price, int stock) {
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("상품 이름은 1~100자여야 합니다.");
        }
        if (price == null || price.signum() <= 0 || price.scale() > 2) {
            throw new IllegalArgumentException("상품 가격은 소수 둘째 자리까지의 양수여야 합니다.");
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
            throw new IllegalArgumentException("차감 수량은 1 이상이어야 합니다.");
        }
        if (stock < quantity) {
            // 도메인은 HTTP 상태를 모르고 재고 부족이라는 업무 사실만 예외로 표현합니다.
            throw new InsufficientStockException(stock);
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
