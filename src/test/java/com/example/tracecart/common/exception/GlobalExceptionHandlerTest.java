package com.example.tracecart.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tracecart.product.Product;
import com.example.tracecart.product.InsufficientStockException;
import com.example.tracecart.order.application.IdempotencyConflictException;
import com.example.tracecart.order.domain.InvalidOrderException;
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

    @Test
    void mapsDomainValidationToBadRequest() {
        ResponseEntity<ApiError> response =
                handler.handleInvalidOrder(new InvalidOrderException("잘못된 주문 값"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void mapsStockAndIdempotencyConflictsToDifferentCodes() {
        ResponseEntity<ApiError> stock =
                handler.handleInsufficientStock(new InsufficientStockException(1));
        ResponseEntity<ApiError> idempotency =
                handler.handleIdempotencyConflict(new IdempotencyConflictException());

        assertThat(stock.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stock.getBody().code()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(idempotency.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(idempotency.getBody().code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }
}
