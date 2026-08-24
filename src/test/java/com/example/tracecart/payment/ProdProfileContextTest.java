package com.example.tracecart.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.http.client.autoconfigure.HttpClientsProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

// 실제 운영 비밀정보와 외부 인프라 대신 안전한 테스트 값으로 prod 전체 빈 조립을 검증합니다.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tracecart-prod;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.payment.base-url=https://payment.example"
})
@ActiveProfiles("prod")
class ProdProfileContextTest {

    @Autowired ApplicationContext context;
    @Autowired PaymentClient paymentClient;
    @Autowired RestClient.Builder restClientBuilder;
    @Autowired HttpClientsProperties httpClientsProperties;
    @Autowired PaymentScenarioResolver paymentScenarioResolver;

    @Test
    void prodContextStartsWithExactlyOneExternalPaymentClient() {
        Map<String, PaymentClient> clients = context.getBeansOfType(PaymentClient.class);

        assertThat(clients).hasSize(1);
        assertThat(paymentClient).isInstanceOf(ExternalPaymentClient.class);
        assertThat(context.getBeansOfType(FakePaymentClient.class)).isEmpty();
        assertThat(paymentScenarioResolver).isInstanceOf(ProductionPaymentScenarioResolver.class);
        assertThat(restClientBuilder).isNotNull();
    }

    @Test
    void prodProfileBindsFiniteConnectionAndReadTimeouts() {
        // 운영 설정 파일의 값을 Boot가 실제 HTTP 클라이언트 공통 설정에 바인딩했는지 확인합니다.
        assertThat(httpClientsProperties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(httpClientsProperties.getReadTimeout()).isEqualTo(Duration.ofSeconds(3));
    }
}
