package com.example.tracecart.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

// 실제 서버 없이 가짜 HTTP 요청과 응답으로 TraceIdFilter를 검증합니다.
class TraceIdFilterTest {

    // Spring 주입이 필요 없는 필터이므로 직접 생성합니다.
    private final TraceIdFilter filter = new TraceIdFilter();

    // 한 테스트의 MDC가 다음 테스트로 새지 않도록 항상 정리합니다.
    @AfterEach
    void clearMdc() {
        // 현재 테스트 스레드의 MDC 전체를 비웁니다.
        MDC.clear();
    }

    // 정상 헤더 재사용과 선행 MDC 복원을 검증합니다.
    @Test
    void usesIncomingTraceIdAndRestoresPreviousContext() throws Exception {
        // Given: 주문 조회를 나타내는 가짜 GET 요청을 만듭니다.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders/1");
        // 클라이언트가 안전한 traceId를 보낸 상황을 만듭니다.
        request.addHeader(TraceIdFilter.TRACE_HEADER, "portfolio-trace-1234");
        // 필터가 헤더와 상태를 쓸 가짜 응답입니다.
        MockHttpServletResponse response = new MockHttpServletResponse();
        // 필터 체인 내부에서 본 traceId를 보관합니다.
        AtomicReference<String> observed = new AtomicReference<>();
        // 필터보다 앞선 코드가 이미 MDC 값을 가진 상황을 재현합니다.
        MDC.put("existing", "keep-me");

        // When: 필터를 통과시키고 다음 체인에서 현재 traceId를 읽습니다.
        filter.doFilter(request, response, (req, res) -> observed.set(MDC.get("traceId")));

        // Then: 처리 중에는 클라이언트가 보낸 traceId가 보여야 합니다.
        assertThat(observed.get()).isEqualTo("portfolio-trace-1234");
        // Then: 같은 ID가 응답 헤더로도 돌아가야 합니다.
        assertThat(response.getHeader(TraceIdFilter.TRACE_HEADER)).isEqualTo("portfolio-trace-1234");
        // Then: 요청이 끝나면 이번 traceId는 현재 스레드에서 제거돼야 합니다.
        assertThat(MDC.get("traceId")).isNull();
        // Then: 필터 전에 존재했던 값은 그대로 복구돼야 합니다.
        assertThat(MDC.get("existing")).isEqualTo("keep-me");
    }

    // 로그 조작 가능성이 있는 헤더를 서버 생성 ID로 바꾸는지 검증합니다.
    @Test
    void replacesUnsafeTraceId() throws Exception {
        // Given: 단순 상태 확인용 가짜 요청을 만듭니다.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        // 정규식에서 허용하지 않는 공백이 든 헤더를 넣습니다.
        request.addHeader(TraceIdFilter.TRACE_HEADER, "unsafe value with spaces");
        // 필터가 새 헤더를 쓸 가짜 응답을 만듭니다.
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When: 뒤에서 할 일은 없는 빈 필터 체인으로 요청을 통과시킵니다.
        filter.doFilter(request, response, (req, res) -> { });

        // Then: 응답에는 UUID 하이픈을 뺀 32자리 새 ID가 있어야 합니다.
        assertThat(response.getHeader(TraceIdFilter.TRACE_HEADER)).matches("[a-f0-9]{32}");
    }
}
