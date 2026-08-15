package com.example.tracecart.common.exception;

import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 모든 REST Controller에서 던진 예외를 한 장소에서 JSON 응답으로 변환합니다.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // BusinessException이 Controller 밖으로 전파되면 이 메서드가 선택됩니다.
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(BusinessException exception) {
        // 예상 가능한 거절이므로 ERROR 대신 WARN 수준으로 코드와 원인을 남깁니다.
        log.warn("Business request rejected: code={}, message={}", exception.code(), exception.getMessage());
        return error(exception.status(), exception.code(), exception.getMessage());
    }

    // @Valid 검증 실패를 400 응답으로 바꾸는 메서드입니다.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        // 모든 필드 오류를 "필드명: 이유" 문자열 하나로 합칩니다.
        String message = exception.getBindingResult().getFieldErrors().stream()
                // 각 FieldError에서 필드 이름과 기본 메시지만 꺼냅니다.
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                // 오류가 여러 개면 쉼표로 연결합니다.
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    // JSON 문법 오류, 알 수 없는 enum 값, 잘못된 타입은 서버 오류가 아니라 400 요청 오류입니다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        log.warn("HTTP request body could not be read: {}", exception.getMessage());
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "요청 본문 형식이 올바르지 않습니다.");
    }

    // 위에서 별도로 처리한 예상 가능한 예외가 아닌 모든 예외를 마지막 안전망에서 처리합니다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        // 개발자가 원인을 분석할 수 있도록 전체 스택 트레이스를 ERROR로 기록합니다.
        log.error("Unexpected server error", exception);
        // 내부 구현 정보는 숨기고 일반화된 500 메시지만 클라이언트에 보냅니다.
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "예상하지 못한 오류가 발생했습니다.");
    }

    // 모든 예외 처리기가 공통으로 사용하는 ResponseEntity 생성 메서드입니다.
    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        // 현재 시각과 현재 요청 MDC의 traceId를 포함한 오류 본문을 만듭니다.
        ApiError body = new ApiError(Instant.now(), status.value(), code, message, MDC.get("traceId"));
        return ResponseEntity.status(status).body(body);
    }
}
