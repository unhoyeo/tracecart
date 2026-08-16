package com.example.tracecart.payment;

// OrderService가 실행 환경을 몰라도 결제를 요청할 수 있게 만드는 추상화입니다.
public interface PaymentClient {
    // 구현체는 같은 명령을 받아 성공 결과를 반환하거나 PaymentException을 던져야 합니다.
    PaymentResult pay(PaymentCommand command);
}
