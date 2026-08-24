package com.example.tracecart.order.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByIdempotencyKey(String idempotencyKey);

    // 결제 선점과 상태 확정을 짧은 트랜잭션 안에서 직렬화하기 위해 주문 행을 잠급니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchaseOrder from PurchaseOrder purchaseOrder where purchaseOrder.id = :orderId")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("orderId") Long orderId);
}
