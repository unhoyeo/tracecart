package com.example.tracecart.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tracecart.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

// 데모 시나리오 헤더가 비운영 환경에서만 허용되는지 검증합니다.
class PaymentScenarioResolverTest {

    @Test
    void demoResolverDefaultsToSuccessAndParsesCaseInsensitively() {
        DemoPaymentScenarioResolver resolver = new DemoPaymentScenarioResolver();

        assertThat(resolver.resolve(null)).isEqualTo(PaymentScenario.SUCCESS);
        assertThat(resolver.resolve("timeout")).isEqualTo(PaymentScenario.TIMEOUT);
    }

    @Test
    void demoResolverRejectsUnknownScenario() {
        DemoPaymentScenarioResolver resolver = new DemoPaymentScenarioResolver();

        assertThatThrownBy(() -> resolver.resolve("unknown"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code()).isEqualTo("INVALID_PAYMENT_SCENARIO"));
    }

    @Test
    void productionResolverRejectsDemoHeader() {
        ProductionPaymentScenarioResolver resolver = new ProductionPaymentScenarioResolver();

        assertThatThrownBy(() -> resolver.resolve("FAILURE"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code()).isEqualTo("DEMO_FEATURE_NOT_AVAILABLE"));
        assertThat(resolver.resolve(null)).isEqualTo(PaymentScenario.SUCCESS);
    }
}
