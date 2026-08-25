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
            // BEFORE: 기존 값을 지우지 않고 호출자에게 있는 키만 덮어씁니다.
            if (callerContext != null) {
                callerContext.forEach(MDC::put);
            }
            // BEFORE: 작업이 끝난 뒤 풀 스레드의 원래 MDC를 복원하지 않습니다.
            runnable.run();
        };
    }
}
