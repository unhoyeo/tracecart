package com.example.tracecart.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// application.yml에서 app으로 시작하는 값을 이 객체에 타입 안전하게 묶습니다.
@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
        @Valid @NotNull Async async,
        @Valid @NotNull Payment payment
) {
    // app.async 아래의 스레드 풀 설정 세 값을 표현합니다.
    public record Async(
            // 평상시 스레드 개수입니다.
            @Min(1) @Max(128) int corePoolSize,
            // 최대 스레드 개수입니다.
            @Min(1) @Max(256) int maxPoolSize,
            // 대기 큐에 넣을 수 있는 작업 개수입니다.
            @PositiveOrZero @Max(100_000) int queueCapacity
    ) {
        @AssertTrue(message = "최대 스레드 수는 기본 스레드 수보다 작을 수 없습니다.")
        public boolean isPoolRangeValid() {
            return maxPoolSize >= corePoolSize;
        }
    }

    // app.payment 아래의 Fake 결제 지연 시간과 실제 결제 서버 주소를 표현합니다.
    public record Payment(
            // Fake 요청이 기다릴 밀리초입니다.
            @PositiveOrZero long fakeDelayMs,
            // Fake TIMEOUT 시나리오가 기다릴 밀리초입니다. 테스트에서는 0으로 덮어씁니다.
            @PositiveOrZero long fakeTimeoutDelayMs,
            // 실제 결제 서버의 기본 URL입니다.
            @NotBlank
            @Pattern(regexp = "https?://.+", message = "HTTP 또는 HTTPS URL이어야 합니다.")
            String baseUrl
    ) {
    }
}
