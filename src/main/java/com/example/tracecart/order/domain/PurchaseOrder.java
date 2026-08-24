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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "purchase_orders",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_purchase_orders_idempotency_key",
                columnNames = "idempotency_key"
        )
)
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 같은 요청을 재전송해도 주문과 결제가 중복되지 않게 하는 클라이언트 제공 키입니다.
    @Column(name = "idempotency_key", nullable = false, length = 64, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, length = 64, updatable = false)
    private String userId;

    @Column(nullable = false, updatable = false)
    private Long productId;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(length = 100)
    private String transactionId;

    @Column(length = 300)
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    // 동시에 같은 주문 결과를 확정하는 갱신도 감지할 수 있도록 버전을 둡니다.
    @Version
    private long version;

    protected PurchaseOrder() {
    }

    public PurchaseOrder(
            String idempotencyKey,
            OrderUserId userId,
            Long productId,
            OrderQuantity quantity,
            BigDecimal totalPrice
    ) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._-]{8,64}")) {
            throw new InvalidOrderException("멱등성 키는 영문, 숫자, 점, 밑줄, 하이픈으로 된 8~64자여야 합니다.");
        }
        if (productId == null || productId <= 0) {
            throw new InvalidOrderException("상품 ID는 양수여야 합니다.");
        }
        if (totalPrice == null || totalPrice.signum() <= 0 || totalPrice.scale() > 2) {
            throw new InvalidOrderException("주문 금액은 소수 둘째 자리까지의 양수여야 합니다.");
        }
        this.idempotencyKey = idempotencyKey;
        this.userId = userId.value();
        this.productId = productId;
        this.quantity = quantity.value();
        this.totalPrice = totalPrice;
        this.status = OrderStatus.PENDING;
    }

    public boolean hasSameRequest(
            OrderUserId requestedUserId,
            Long requestedProductId,
            OrderQuantity requestedQuantity
    ) {
        return userId.equals(requestedUserId.value())
                && productId.equals(requestedProductId)
                && quantity == requestedQuantity.value();
    }

    public void startPayment() {
        requireStatus(OrderStatus.PENDING);
        status = OrderStatus.PAYMENT_PROCESSING;
    }

    public void markPaid(String approvedTransactionId) {
        if (approvedTransactionId == null || approvedTransactionId.isBlank() || approvedTransactionId.length() > 100) {
            throw new InvalidOrderException("유효한 결제 거래 ID가 필요합니다.");
        }
        if (status != OrderStatus.PAYMENT_PROCESSING && status != OrderStatus.PAYMENT_UNKNOWN) {
            throw new InvalidOrderException("결제 처리 중이거나 결과 확인 중인 주문만 결제 완료할 수 있습니다.");
        }
        status = OrderStatus.PAID;
        transactionId = approvedTransactionId;
        failureReason = null;
    }

    public void markPaymentDeclined(String reason) {
        requireStatus(OrderStatus.PAYMENT_PROCESSING);
        status = OrderStatus.PAYMENT_DECLINED;
        failureReason = normalizedReason(reason);
    }

    public void markPaymentUnknown(String reason) {
        requireStatus(OrderStatus.PAYMENT_PROCESSING);
        status = OrderStatus.PAYMENT_UNKNOWN;
        failureReason = normalizedReason(reason);
    }

    private void requireStatus(OrderStatus expected) {
        if (status != expected) {
            throw new InvalidOrderException(expected + " 상태의 주문만 이 작업을 수행할 수 있습니다. 현재 상태: " + status);
        }
    }

    private String normalizedReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidOrderException("결제 미완료 사유는 비어 있을 수 없습니다.");
        }
        return reason.length() <= 300 ? reason : reason.substring(0, 300);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getUserId() { return userId; }
    public Long getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public OrderStatus getStatus() { return status; }
    public String getTransactionId() { return transactionId; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
