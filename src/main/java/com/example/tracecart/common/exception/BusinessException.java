package com.example.tracecart.common.exception;

import org.springframework.http.HttpStatus;

// 상품이나 주문을 찾지 못한 것처럼 예상 가능한 업무 오류를 표현합니다.
public class BusinessException extends RuntimeException {

    // 이 오류를 HTTP 응답으로 바꿀 때 사용할 상태 코드입니다.
    private final HttpStatus status;
    // 프론트엔드가 오류 종류를 안정적으로 구분할 문자열 코드입니다.
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        // RuntimeException에도 메시지를 전달해 로그와 getMessage()에서 사용할 수 있게 합니다.
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
