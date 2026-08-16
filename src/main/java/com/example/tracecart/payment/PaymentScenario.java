package com.example.tracecart.payment;

// local/dev에서 외부 서버 없이 서로 다른 결제 결과를 재현하기 위한 선택지입니다.
public enum PaymentScenario {
    // 결제 승인과 알림 전송 흐름을 실행합니다.
    SUCCESS,
    // 결제 거절과 재고 복원 흐름을 실행합니다.
    FAILURE,
    // 지연 뒤 타임아웃 실패와 재고 복원 흐름을 실행합니다.
    TIMEOUT
}
