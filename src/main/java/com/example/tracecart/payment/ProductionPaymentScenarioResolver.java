package com.example.tracecart.payment;

import com.example.tracecart.common.exception.BusinessException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

// prod에서는 클라이언트가 Fake 결제 결과를 제어하지 못하도록 데모 헤더를 거절합니다.
@Component
@Profile("prod")
public class ProductionPaymentScenarioResolver implements PaymentScenarioResolver {

    @Override
    public PaymentScenario resolve(String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "DEMO_FEATURE_NOT_AVAILABLE",
                    "운영 환경에서는 데모 결제 시나리오를 사용할 수 없습니다."
            );
        }
        // ExternalPaymentClient는 이 값을 외부 요청으로 보내지 않으므로 기본값은 실행 결과에 영향을 주지 않습니다.
        return PaymentScenario.SUCCESS;
    }
}
