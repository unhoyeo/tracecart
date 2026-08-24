package com.example.tracecart.payment;

import com.example.tracecart.common.exception.BusinessException;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

// local, dev, test에서만 요청별 성공·거절·타임아웃을 선택할 수 있게 합니다.
@Component
@Profile("local | dev | test")
public class DemoPaymentScenarioResolver implements PaymentScenarioResolver {

    @Override
    public PaymentScenario resolve(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return PaymentScenario.SUCCESS;
        }
        try {
            return PaymentScenario.valueOf(candidate.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAYMENT_SCENARIO",
                    "데모 결제 시나리오는 SUCCESS, FAILURE, TIMEOUT 중 하나여야 합니다."
            );
        }
    }
}
