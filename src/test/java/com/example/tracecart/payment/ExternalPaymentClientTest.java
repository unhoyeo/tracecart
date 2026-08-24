package com.example.tracecart.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.tracecart.common.config.AppProperties;
import com.example.tracecart.common.logging.TraceIdFilter;
import java.net.SocketTimeoutException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.slf4j.MDC;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// 실제 운영 결제 서버 대신 Mock HTTP 서버를 연결해 요청과 응답 계약을 검증합니다.
class ExternalPaymentClientTest {

    private MockRestServiceServer server;
    private ExternalPaymentClient paymentClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AppProperties properties = new AppProperties(
                new AppProperties.Async(1, 1, 1),
                new AppProperties.Payment(0, 0, "https://payment.example")
        );
        paymentClient = new ExternalPaymentClient(builder, properties);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void returnsTransactionIdForSuccessfulProductionResponse() {
        MDC.put(TraceIdFilter.TRACE_ID, "external-trace-0001");
        server.expect(requestTo("https://payment.example/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "idem-external-0001"))
                .andExpect(header(TraceIdFilter.TRACE_HEADER, "external-trace-0001"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "orderId": 100,
                          "userId": "user-1",
                          "amount": 15000.00
                        }
                        """))
                .andRespond(withSuccess("""
                        {"transactionId":"prod-tx-100"}
                        """, MediaType.APPLICATION_JSON));

        PaymentResult result = paymentClient.pay(command());

        assertThat(result.transactionId()).isEqualTo("prod-tx-100");
        server.verify();
    }

    @Test
    void convertsFiveHundredResponseToPaymentException() {
        server.expect(requestTo("https://payment.example/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> paymentClient.pay(command()))
                .isInstanceOfSatisfying(PaymentException.class, exception ->
                        assertThat(exception.type()).isEqualTo(PaymentFailureType.UNAVAILABLE));
        server.verify();
    }

    @Test
    void convertsNetworkTimeoutToPaymentException() {
        server.expect(requestTo("https://payment.example/payments"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        assertThatThrownBy(() -> paymentClient.pay(command()))
                .isInstanceOfSatisfying(PaymentException.class, exception ->
                        assertThat(exception.type()).isEqualTo(PaymentFailureType.TIMEOUT));
        server.verify();
    }

    @Test
    void rejectsSuccessfulResponseWithEmptyBody() {
        server.expect(requestTo("https://payment.example/payments"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> paymentClient.pay(command()))
                .isInstanceOfSatisfying(PaymentException.class, exception ->
                        assertThat(exception.type()).isEqualTo(PaymentFailureType.INVALID_RESPONSE));
        server.verify();
    }

    @Test
    void rejectsSuccessfulResponseWithoutTransactionId() {
        server.expect(requestTo("https://payment.example/payments"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> paymentClient.pay(command()))
                .isInstanceOfSatisfying(PaymentException.class, exception ->
                        assertThat(exception.type()).isEqualTo(PaymentFailureType.INVALID_RESPONSE));
        server.verify();
    }

    @Test
    void rejectsSuccessfulResponseWithBlankTransactionId() {
        server.expect(requestTo("https://payment.example/payments"))
                .andRespond(withSuccess("{\"transactionId\":\"   \"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> paymentClient.pay(command()))
                .isInstanceOfSatisfying(PaymentException.class, exception ->
                        assertThat(exception.type()).isEqualTo(PaymentFailureType.INVALID_RESPONSE));
        server.verify();
    }

    @Test
    void convertsMalformedSuccessJsonToPaymentException() {
        server.expect(requestTo("https://payment.example/payments"))
                .andRespond(withSuccess("{not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> paymentClient.pay(command()))
                .isInstanceOfSatisfying(PaymentException.class, exception ->
                        assertThat(exception.type()).isEqualTo(PaymentFailureType.INVALID_RESPONSE));
        server.verify();
    }

    @Test
    void convertsExplicitPaymentRejectionToDeclinedType() {
        server.expect(requestTo("https://payment.example/payments"))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED));

        assertThatThrownBy(() -> paymentClient.pay(command()))
                .isInstanceOfSatisfying(PaymentException.class, exception ->
                        assertThat(exception.type()).isEqualTo(PaymentFailureType.DECLINED));
        server.verify();
    }

    private PaymentCommand command() {
        return new PaymentCommand(
                100L,
                "user-1",
                new BigDecimal("15000.00"),
                "idem-external-0001",
                PaymentScenario.SUCCESS
        );
    }
}
