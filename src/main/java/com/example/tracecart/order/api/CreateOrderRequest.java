package com.example.tracecart.order.api;

import com.example.tracecart.payment.PaymentScenario;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

// POST /api/orders의 JSON 요청 본문을 받는 불변 DTO입니다.
public record CreateOrderRequest(
        // 사용자 ID는 비어 있을 수 없습니다.
        @NotBlank
        // 로그와 저장소에 안전한 문자만 허용하고 길이도 제한합니다.
        @Pattern(regexp = "[A-Za-z0-9._-]{3,64}")
        String userId,

        // 어떤 상품을 주문할지 반드시 지정해야 합니다.
        @NotNull
        @Positive
        Long productId,

        // 수량은 최소 1개, 한 요청에서 최대 100개만 허용합니다.
        @Min(1) @Max(100)
        int quantity,

        // 성공, 실패, 타임아웃 중 재현할 결제 시나리오가 반드시 필요합니다.
        @NotNull
        PaymentScenario paymentScenario
) {
}
