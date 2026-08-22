package com.example.tracecart.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.tracecart.common.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

// 전체 서버를 띄우지 않고 각 프로파일이 선택하는 PaymentClient 구현만 빠르게 검증합니다.
class PaymentClientProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ProfileConfiguration.class)
            .withPropertyValues(
                    "app.async.core-pool-size=1",
                    "app.async.max-pool-size=1",
                    "app.async.queue-capacity=1",
                    "app.payment.fake-delay-ms=0",
                    "app.payment.fake-timeout-delay-ms=0",
                    "app.payment.base-url=https://payment.example"
            );

    @Test
    void localProfileUsesFakePaymentClient() {
        assertProfileSelects("local", FakePaymentClient.class);
    }

    @Test
    void devProfileUsesFakePaymentClient() {
        assertProfileSelects("dev", FakePaymentClient.class);
    }

    @Test
    void testProfileUsesFakePaymentClient() {
        assertProfileSelects("test", FakePaymentClient.class);
    }

    @Test
    void prodProfileUsesExternalPaymentClient() {
        assertProfileSelects("prod", ExternalPaymentClient.class);
    }

    private void assertProfileSelects(String profile, Class<? extends PaymentClient> expectedType) {
        contextRunner
                .withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> {
                    assertThat(context).hasSingleBean(PaymentClient.class);
                    assertThat(context.getBean(PaymentClient.class)).isInstanceOf(expectedType);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    @Import({FakePaymentClient.class, ExternalPaymentClient.class})
    static class ProfileConfiguration {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }
}
