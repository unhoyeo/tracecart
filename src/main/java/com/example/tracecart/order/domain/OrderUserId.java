package com.example.tracecart.order.domain;

import java.util.regex.Pattern;

// 주문자 식별자의 형식 규칙을 한 곳에서 보장하는 값 객체입니다.
public record OrderUserId(String value) {

    public static final String REGEXP = "[A-Za-z0-9._-]{3,64}";
    private static final Pattern VALID_FORMAT = Pattern.compile(REGEXP);

    public OrderUserId {
        if (value == null || !VALID_FORMAT.matcher(value).matches()) {
            throw new InvalidOrderException("사용자 ID는 영문, 숫자, 점, 밑줄, 하이픈으로 된 3~64자여야 합니다.");
        }
    }
}
