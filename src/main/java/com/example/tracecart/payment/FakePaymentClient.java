package com.example.tracecart.payment;

import com.example.tracecart.common.config.AppProperties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
// prod가 아닐 때만 활성화되어 로컬 개발과 테스트에서 외부 결제를 대체합니다.
@Profile("local | dev | test")
public class FakePaymentClient implements PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(FakePaymentClient.class);
    // application.yml의 Fake 결제 지연 시간을 읽기 위한 설정 객체입니다.
    private final AppProperties properties;

    public FakePaymentClient(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentResult pay(PaymentCommand command) {
        log.info("Fake payment requested: scenario={}, amount={}", command.scenario(), command.amount());
        simulateDelay(command.scenario());
        return switch (command.scenario()) {
            // 성공이면 가짜 거래 ID를 발급한 PaymentResult를 반환합니다.
            case SUCCESS -> new PaymentResult("fake-" + UUID.randomUUID());
            case FAILURE -> throw new PaymentException(
                    PaymentFailureType.DECLINED,
                    "결제가 거절되었습니다."
            );
            case TIMEOUT -> throw new PaymentException(
                    PaymentFailureType.TIMEOUT,
                    "결제 서버 응답 시간이 초과되어 결과를 확인 중입니다."
            );
        };
    }

    // 실제 네트워크 호출처럼 보이도록 짧은 지연을 흉내 내는 내부 메서드입니다.
    private void simulateDelay(PaymentScenario scenario) {
        // TIMEOUT 지연을 별도 설정으로 분리해 테스트에서는 기다리지 않고 분기만 검증할 수 있습니다.
        long delay = scenario == PaymentScenario.TIMEOUT
                ? properties.payment().fakeTimeoutDelayMs()
                : properties.payment().fakeDelayMs();
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            // 인터럽트 상태를 다시 표시해 상위 실행기가 종료 신호를 잃지 않게 합니다.
            Thread.currentThread().interrupt();
            // 중단된 결제도 결과 미확정 주문으로 남도록 유형이 있는 PaymentException으로 바꿉니다.
            throw new PaymentException(
                    PaymentFailureType.INTERRUPTED,
                    "결제 처리가 중단되어 결과를 확인 중입니다.",
                    exception
            );
        }
    }
}
