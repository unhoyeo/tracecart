package com.example.tracecart.payment;

// 결제가 승인됐을 때 결제 서버가 반환하는 최소 결과를 표현합니다.
// transactionId는 같은 결제를 결제 시스템 로그와 대조할 때 사용하는 식별자입니다.
public record PaymentResult(String transactionId) {
}
