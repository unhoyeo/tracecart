package com.example.tracecart.common.logging;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

// 스레드가 바뀌어도 traceId 같은 MDC 값이 이어지게 만드는 작업 장식자입니다.
public class MdcTaskDecorator implements TaskDecorator {

    // 스레드 풀에 작업이 제출될 때 Spring이 이 메서드를 호출합니다.
    @Override
    public Runnable decorate(Runnable runnable) {
        // 이 줄은 요청 스레드에서 실행되므로 현재 요청의 MDC 전체를 복사할 수 있습니다.
        Map<String, String> callerContext = MDC.getCopyOfContextMap();

        // 원래 작업을 감싼 새 작업을 스레드 풀에 전달합니다.
        return () -> {
            // 풀의 스레드가 이전부터 가지고 있던 MDC도 나중에 되돌리기 위해 보관합니다.
            Map<String, String> executorContext = MDC.getCopyOfContextMap();
            try {
                // 비동기 스레드의 MDC를 요청 스레드에서 복사한 값으로 교체합니다.
                replaceContext(callerContext);
                // 이제 원래 비동기 메서드를 실행하므로 그 안의 로그에도 같은 traceId가 찍힙니다.
                runnable.run();
            } finally {
                // 성공하거나 예외가 발생해도 풀 스레드에 요청 정보가 남지 않도록 원래 상태로 복구합니다.
                replaceContext(executorContext);
            }
        };
    }

    // MDC를 전달받은 상태로 정확히 교체하는 공통 메서드입니다.
    private void replaceContext(Map<String, String> context) {
        MDC.clear();
        if (context != null) {
            MDC.setContextMap(context);
        }
    }
}
