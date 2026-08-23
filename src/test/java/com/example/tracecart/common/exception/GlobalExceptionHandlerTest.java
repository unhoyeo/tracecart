package com.example.tracecart.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tracecart.product.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

// Spring MVC 없이 예외와 HTTP 오류 응답의 매핑만 검증하는 단위 테스트입니다.
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void mapsOptimisticLockConflictToTraceableConflictResponse() {
        MDC.put("traceId", "concurrent-trace-1234");
        ObjectOptimisticLockingFailureException exception =
                new ObjectOptimisticLockingFailureException(Product.class, 1L);

        ResponseEntity<ApiError> response = handler.handleOptimisticLock(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONCURRENT_STOCK_UPDATE");
        assertThat(response.getBody().traceId()).isEqualTo("concurrent-trace-1234");
    }
}
