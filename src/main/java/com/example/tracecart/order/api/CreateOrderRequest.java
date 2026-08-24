package com.example.tracecart.order.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import com.example.tracecart.order.domain.OrderQuantity;
import com.example.tracecart.order.domain.OrderUserId;

// POST /api/orders의 JSON 요청 본문을 받는 불변 DTO입니다.
public record CreateOrderRequest(
        // 사용자 ID는 비어 있을 수 없습니다.
        @NotBlank
        // 로그와 저장소에 안전한 문자만 허용하고 길이도 제한합니다.
        @Pattern(regexp = OrderUserId.REGEXP)
        String userId,

        // 어떤 상품을 주문할지 반드시 지정해야 합니다.
        @NotNull
        @Positive
        Long productId,

        // 수량은 최소 1개, 한 요청에서 최대 100개만 허용합니다.
        @Min(OrderQuantity.MIN) @Max(OrderQuantity.MAX)
        int quantity
) {
}
