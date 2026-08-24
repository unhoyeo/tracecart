package com.example.tracecart.common.logging;

import java.util.Objects;
import org.slf4j.MDC;

// try-with-resources 블록 동안만 특정 MDC 값을 유지하는 작은 도우미입니다.
public final class MdcScope implements AutoCloseable {

    // 이 스코프가 관리하는 MDC 키입니다. (예: userId 또는 orderId)
    private final String key;
    // 스코프가 끝날 때 복구할 수 있도록 변경 전 값을 기억합니다.
    private final String previousValue;

    // 외부에서는 정적 팩토리 메서드 with를 통해서만 만들도록 생성자를 숨깁니다.
    private MdcScope(String key, String value) {
        Objects.requireNonNull(key, "MDC 키는 null일 수 없습니다.");
        Objects.requireNonNull(value, "MDC 값은 null일 수 없습니다.");
        // close()에서 어떤 MDC 항목을 정리할지 알 수 있도록 키 이름을 저장합니다.
        this.key = key;
        // 같은 키가 있으면 기존 값을, 없으면 null을 저장해 close()에서 복원 여부를 판단합니다.
        this.previousValue = MDC.get(key);
        // 기존 값이 있더라도 블록에서 사용할 새 값으로 임시 교체합니다.
        MDC.put(key, value);
    }

    // 숫자 ID 등 어떤 객체가 와도 문자열로 바꿔 스코프를 생성합니다.
    public static MdcScope with(String key, Object value) {
        return new MdcScope(key, Objects.requireNonNull(value, "MDC 값은 null일 수 없습니다.").toString());
    }

    // try-with-resources 블록이 끝나면 Java가 자동으로 호출합니다.
    @Override
    public void close() {
        // 원래 값이 없었다면 이번 스코프에서 넣은 키를 완전히 제거합니다.
        if (previousValue == null) {
            MDC.remove(key);
        } else {
            // 중첩 스코프였다면 바깥 스코프가 사용하던 값으로 되돌립니다.
            MDC.put(key, previousValue);
        }
    }
}
