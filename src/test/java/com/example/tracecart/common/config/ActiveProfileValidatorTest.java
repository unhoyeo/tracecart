package com.example.tracecart.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

// 상충하거나 알 수 없는 프로파일이 안전하게 기동 실패하는지 검증합니다.
class ActiveProfileValidatorTest {

    @Test
    void acceptsExactlyOneKnownProfile() {
        ActiveProfileValidator validator = validatorWithActiveProfiles("prod");

        assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void rejectsMixedProfiles() {
        ActiveProfileValidator validator = validatorWithActiveProfiles("local", "prod");

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("정확히 하나");
    }

    @Test
    void rejectsUnknownProfile() {
        ActiveProfileValidator validator = validatorWithActiveProfiles("production");

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("production");
    }

    private ActiveProfileValidator validatorWithActiveProfiles(String... profiles) {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(profiles);
        return new ActiveProfileValidator(environment);
    }
}
