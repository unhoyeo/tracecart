package com.example.tracecart.order.domain;

import java.util.regex.Pattern;

// 주문 재전송을 동일 요청으로 식별하는 멱등성 키 값 객체입니다.
public record IdempotencyKey(String value) {

    public static final String HEADER = "Idempotency-Key";
    public static final String REGEXP = "[A-Za-z0-9._-]{8,64}";
    private static final Pattern VALID_FORMAT = Pattern.compile(REGEXP);

    public IdempotencyKey {
        if (value == null || !VALID_FORMAT.matcher(value).matches()) {
            throw new InvalidOrderException("멱등성 키는 영문, 숫자, 점, 밑줄, 하이픈으로 된 8~64자여야 합니다.");
        }
    }
}
