package com.example.tracecart.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String userId;

    // 현재 데모 주문은 하나의 상품만 가지므로 상품 ID를 직접 저장합니다.
    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(length = 300)
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // JPA가 조회 결과로 엔티티를 생성할 때 사용하는 기본 생성자입니다.
    protected PurchaseOrder() {
    }

    // 새 주문을 만들 때 필요한 업무 값만 받는 생성자입니다.
    public PurchaseOrder(String userId, Long productId, int quantity, BigDecimal totalPrice) {
        // Controller를 거치지 않는 호출에서도 빈 사용자 ID가 저장되지 않도록 도메인이 방어합니다.
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("사용자 ID는 비어 있을 수 없습니다.");
        }
        // 데이터베이스에 존재할 수 없는 0 이하 상품 ID를 주문이 보유하지 못하게 합니다.
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("상품 ID는 양수여야 합니다.");
        }
        // 음수 주문이 재고를 늘리거나 0개 주문이 생기는 것을 생성 시점에 차단합니다.
        if (quantity <= 0) {
            throw new IllegalArgumentException("주문 수량은 1 이상이어야 합니다.");
        }
        // 금액이 없거나 0 이하인 비정상 주문은 결제 단계로 넘어가지 못하게 합니다.
        if (totalPrice == null || totalPrice.signum() <= 0) {
            throw new IllegalArgumentException("주문 금액은 0보다 커야 합니다.");
        }
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        // 결제 전 새 주문의 초기 상태는 항상 PENDING입니다.
        this.status = OrderStatus.PENDING;
    }

    // 결제 승인을 엔티티 상태에 반영하는 도메인 메서드입니다.
    public void markPaid() {
        this.status = OrderStatus.PAID;
        // 이전 실패 정보가 남지 않도록 실패 사유를 비웁니다.
        this.failureReason = null;
    }

    // 결제 실패를 상태와 이유 두 값으로 함께 반영합니다.
    public void markPaymentFailed(String reason) {
        this.status = OrderStatus.PAYMENT_FAILED;
        this.failureReason = reason;
    }

    // JPA가 INSERT를 실행하기 직전에 자동으로 호출합니다.
    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    // JPA가 UPDATE를 실행하기 직전에 자동으로 호출합니다.
    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
