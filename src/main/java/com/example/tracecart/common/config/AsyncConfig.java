package com.example.tracecart.common.config;

import com.example.tracecart.common.logging.MdcTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 애플리케이션에서 사용할 비동기 실행기를 만드는 Spring 설정 클래스입니다.
@Configuration
public class AsyncConfig {

    // @Async("applicationTaskExecutor")가 찾을 수 있도록 빈 이름을 명시합니다.
    @Bean(name = "applicationTaskExecutor")
    public TaskExecutor applicationTaskExecutor(AppProperties properties) {
        AppProperties.Async config = properties.async();
        // 매 작업마다 새 스레드를 만들지 않고 재사용하는 Spring 스레드 풀을 생성합니다.
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notification-");
        executor.setCorePoolSize(config.corePoolSize());
        executor.setMaxPoolSize(config.maxPoolSize());
        executor.setQueueCapacity(config.queueCapacity());
        // 요청 스레드의 MDC를 비동기 스레드로 복사하는 장식을 모든 작업에 적용합니다.
        executor.setTaskDecorator(new MdcTaskDecorator());
        // 애플리케이션 종료 시 실행 중인 알림 작업을 가능한 한 마치도록 기다립니다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        // 지금까지 지정한 값으로 실제 내부 스레드 풀을 초기화합니다.
        executor.initialize();
        return executor;
    }
}
