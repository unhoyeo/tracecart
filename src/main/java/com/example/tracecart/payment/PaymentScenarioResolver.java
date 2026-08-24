package com.example.tracecart.payment;

// 데모 결제 결과 제어 헤더를 실행 환경에 맞게 해석합니다.
public interface PaymentScenarioResolver {

    String HEADER = "X-Demo-Payment-Scenario";

    PaymentScenario resolve(String candidate);
}
