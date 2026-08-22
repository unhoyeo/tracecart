package com.example.tracecart.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

// RestClient 스타터가 생성자 주입에 필요한 Builder 빈을 실제로 제공하는지 검증합니다.
@SpringBootTest
// 외부 데이터베이스 없이 H2를 사용하도록 test 프로파일을 활성화합니다.
@ActiveProfiles("test")
class RestClientAutoConfigurationTest {

    // 빈이 없다면 테스트 컨텍스트 시작 단계에서 즉시 실패합니다.
    @Autowired
    RestClient.Builder restClientBuilder;

    // 자동 설정된 Builder가 Spring 컨테이너에 존재하는지 확인합니다.
    @Test
    void providesRestClientBuilderBean() {
        // 생성자 주입에 사용할 Builder가 null이 아니어야 합니다.
        assertThat(restClientBuilder).isNotNull();
    }
}
