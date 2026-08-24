package com.example.tracecart.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

// 중첩 스코프와 예외 종료에서도 기존 MDC가 정확히 복구되는지 검증합니다.
class MdcScopeTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void nestedScopeRestoresOuterValue() {
        MDC.put("orderId", "outer");

        try (MdcScope ignored = MdcScope.with("orderId", "inner")) {
            assertThat(MDC.get("orderId")).isEqualTo("inner");
        }

        assertThat(MDC.get("orderId")).isEqualTo("outer");
    }

    @Test
    void scopeRemovesNewKeyAfterException() {
        assertThatThrownBy(() -> {
            try (MdcScope ignored = MdcScope.with("userId", "user-1")) {
                throw new IllegalStateException("boom");
            }
        }).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get("userId")).isNull();
    }

    @Test
    void rejectsNullKeyAndValue() {
        assertThatThrownBy(() -> MdcScope.with(null, "value"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MdcScope.with("key", null))
                .isInstanceOf(NullPointerException.class);
    }
}
