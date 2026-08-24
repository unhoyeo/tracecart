package com.example.tracecart.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

// 잘못된 운영 설정이 애플리케이션 기동 전에 Bean Validation으로 거절되는지 검증합니다.
class AppPropertiesValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidConfiguration() {
        AppProperties properties = properties(4, 8, 100, 50, 300, "https://payment.example");

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsInvalidThreadPoolDelayAndUrl() {
        AppProperties properties = properties(8, 4, -1, -1, -1, "not-a-url");

        assertThat(validator.validate(properties))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(
                        "async.poolRangeValid",
                        "async.queueCapacity",
                        "payment.fakeDelayMs",
                        "payment.fakeTimeoutDelayMs",
                        "payment.baseUrl"
                );
    }

    private AppProperties properties(
            int core,
            int max,
            int queue,
            long delay,
            long timeoutDelay,
            String baseUrl
    ) {
        return new AppProperties(
                new AppProperties.Async(core, max, queue),
                new AppProperties.Payment(delay, timeoutDelay, baseUrl)
        );
    }
}
