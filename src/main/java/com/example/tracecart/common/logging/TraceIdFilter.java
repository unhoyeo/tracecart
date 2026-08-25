package com.example.tracecart.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Spring이 이 필터를 자동으로 찾아 서블릿 필터 체인에 등록합니다.
@Component
// 다른 필터보다 먼저 실행되어 뒤의 모든 로그가 traceId를 사용할 수 있게 합니다.
@Order(Ordered.HIGHEST_PRECEDENCE)
// 한 HTTP 요청 안에서 정확히 한 번만 실행되는 Spring 제공 필터를 상속합니다.
public class TraceIdFilter extends OncePerRequestFilter {

    // 코드 여러 곳에서 같은 MDC 키를 오타 없이 사용하기 위한 상수입니다.
    public static final String TRACE_ID = "traceId";
    // 클라이언트와 서버가 추적 ID를 주고받을 HTTP 헤더 이름입니다.
    public static final String TRACE_HEADER = "X-Trace-Id";
    // 로그 조작과 과도한 길이를 막기 위해 허용할 ID 문자와 길이를 제한합니다.
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    // 이 클래스 이름으로 Logback에 로그 이벤트를 전달할 SLF4J 로거입니다.
    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    // 실제 HTTP 요청이 필터를 통과할 때 호출되는 핵심 메서드입니다.
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain // 다음 필터 또는 최종 Controller로 요청을 넘기는 체인입니다.
    ) throws ServletException, IOException {

        // 요청 헤더가 안전하면 재사용하고, 아니면 서버가 새 traceId를 만듭니다.
        String traceId = resolveTraceId(request.getHeader(TRACE_HEADER));
        long startedAt = System.nanoTime();

        try {
            MDC.put(TRACE_ID, traceId);
            MDC.put("httpMethod", request.getMethod());
            MDC.put("requestUri", request.getRequestURI());
            // 사용자 헤더도 안전한 형식일 때만 MDC에 넣습니다.
            putIfSafe("userId", request.getHeader("X-User-Id"));
            // 장애 신고 시 클라이언트가 traceId를 확인할 수 있도록 응답에도 돌려줍니다.
            response.setHeader(TRACE_HEADER, traceId);
            log.info("HTTP request started");
            // 이 줄에서 Controller와 Service를 포함한 나머지 요청 처리가 실행됩니다.
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            MDC.put("status", String.valueOf(response.getStatus()));
            MDC.put("elapsedMs", String.valueOf(elapsedMs));
            log.info("HTTP request completed");
            // BEFORE: 요청이 끝나도 MDC를 비우지 않아 재사용되는 Tomcat 스레드에 값이 남습니다.
        }
    }

    // 클라이언트가 보낸 ID를 사용할지 새로 만들지 결정합니다.
    private String resolveTraceId(String candidate) {
        if (candidate != null && SAFE_ID.matcher(candidate).matches()) {
            return candidate;
        }
        // UUID의 하이픈을 제거해 32자리 안전한 새 추적 ID를 만듭니다.
        return UUID.randomUUID().toString().replace("-", "");
    }

    // 선택적인 헤더 값을 검증한 뒤 MDC에 넣는 메서드입니다.
    private void putIfSafe(String key, String candidate) {
        if (candidate != null && SAFE_ID.matcher(candidate).matches()) {
            MDC.put(key, candidate);
        }
    }
}
