package com.example.tracecart.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

// 프로파일별 Logback 설정의 핵심 appender와 설정 가능한 롤링 경로를 확인합니다.
class LogbackProfileConfigurationTest {

    @Test
    void definesHumanReadableLocalAndStructuredDevProdOutputs() throws IOException {
        String configuration;
        try (var stream = getClass().getResourceAsStream("/logback-spring.xml")) {
            if (stream == null) {
                throw new AssertionError("logback-spring.xml을 찾을 수 없습니다.");
            }
            configuration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(configuration)
                .contains("<springProfile name=\"local\">")
                .contains("<springProfile name=\"dev\">")
                .contains("<springProfile name=\"prod\">")
                .contains("org.springframework.boot.logging.logback.StructuredLogEncoder")
                .contains("<fileNamePattern>${logFile}.%d{yyyy-MM-dd}.%i.gz</fileNamePattern>");
    }
}
