package com.example.tracecart.payment;

import com.example.tracecart.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// 운영 환경에서만 실제 HTTP 결제 서버를 호출할 Spring 빈입니다.
@Component
// prod 프로파일이 활성화됐을 때만 이 구현을 등록합니다.
@Profile("prod")
public class ExternalPaymentClient implements PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalPaymentClient.class);
    // Spring Framework가 제공하는 동기식 HTTP 클라이언트입니다.
    private final RestClient restClient;

    public ExternalPaymentClient(RestClient.Builder builder, AppProperties properties) {
        this.restClient = builder.baseUrl(properties.payment().baseUrl()).build();
    }

    @Override
    public PaymentResult pay(PaymentCommand command) {
        try {
            log.info("External payment requested: amount={}", command.amount());
            // POST 응답을 역직렬화한 결과를 받을 변수를 선언합니다.
            PaymentResult response = restClient.post()
                    // 기본 주소 뒤에 실제 결제 엔드포인트 경로를 붙입니다.
                    .uri("/payments")
                    // 데모 전용 scenario를 제외한 운영 결제 요청 DTO를 JSON으로 직렬화합니다.
                    .body(ExternalPaymentRequest.from(command))
                    // HTTP 요청을 보내고 응답 상태 처리를 시작합니다.
                    .retrieve()
                    // 성공 JSON을 PaymentResult 객체로 변환합니다.
                    .body(PaymentResult.class);
            // 2xx였지만 본문이 없으면 정상 승인으로 간주할 수 없습니다.
            if (response == null || response.transactionId() == null || response.transactionId().isBlank()) {
                // 빈 본문뿐 아니라 거래 ID가 누락되거나 공백인 2xx 응답도 승인으로 취급하지 않습니다.
                throw new PaymentException("결제 서버가 유효하지 않은 응답을 반환했습니다.");
            }
            return response;
        } catch (RestClientException exception) {
            // 연결 실패, 타임아웃, 비정상 HTTP 상태를 공통 결제 예외로 추상화합니다.
            throw new PaymentException("외부 결제 요청에 실패했습니다.");
        }
    }
}
