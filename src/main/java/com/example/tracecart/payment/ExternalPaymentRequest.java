package com.example.tracecart.payment;

import java.math.BigDecimal;

// 운영 결제 서버에는 Fake 테스트용 scenario를 보내지 않고 실제 업무 필드만 전달합니다.
record ExternalPaymentRequest(
        // 결제와 내부 주문을 연결할 식별자입니다.
        Long orderId,
        // 결제를 요청한 사용자 식별자입니다.
        String userId,
        // 외부 결제 서버가 승인할 금액입니다.
        BigDecimal amount
) {

    // 내부 결제 명령에서 운영 API에 필요한 값만 선택합니다.
    static ExternalPaymentRequest from(PaymentCommand command) {
        // PaymentScenario는 local/dev 테스트 제어값이므로 의도적으로 복사하지 않습니다.
        return new ExternalPaymentRequest(command.orderId(), command.userId(), command.amount());
    }
}
