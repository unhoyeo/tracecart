package com.example.tracecart.payment;

import com.example.tracecart.common.config.AppProperties;
import com.example.tracecart.common.logging.TraceIdFilter;
import java.net.SocketTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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
                    // 같은 주문 재시도에서 결제사가 중복 승인하지 않도록 멱등성 키를 전달합니다.
                    .header("Idempotency-Key", command.idempotencyKey())
                    // 결제 서버 로그에서도 같은 요청을 찾을 수 있도록 현재 추적 ID를 전달합니다.
                    .headers(headers -> {
                        String traceId = MDC.get(TraceIdFilter.TRACE_ID);
                        if (traceId != null) {
                            headers.set(TraceIdFilter.TRACE_HEADER, traceId);
                        }
                    })
                    // 데모 전용 scenario를 제외한 운영 결제 요청 DTO를 JSON으로 직렬화합니다.
                    .body(ExternalPaymentRequest.from(command))
                    // HTTP 요청을 보내고 응답 상태 처리를 시작합니다.
                    .retrieve()
                    // 성공 JSON을 PaymentResult 객체로 변환합니다.
                    .body(PaymentResult.class);
            // 2xx였지만 본문이 없으면 정상 승인으로 간주할 수 없습니다.
            if (response == null || response.transactionId() == null || response.transactionId().isBlank()) {
                // 빈 본문뿐 아니라 거래 ID가 누락되거나 공백인 2xx 응답도 승인으로 취급하지 않습니다.
                throw new PaymentException(
                        PaymentFailureType.INVALID_RESPONSE,
                        "결제 서버가 유효하지 않은 응답을 반환했습니다."
                );
            }
            return response;
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode().value() == 402 || exception.getStatusCode().value() == 422) {
                throw paymentFailure(
                        PaymentFailureType.DECLINED,
                        "결제가 거절되었습니다.",
                        exception
                );
            }
            throw paymentFailure(
                    PaymentFailureType.INVALID_RESPONSE,
                    "결제 서버가 요청을 처리할 수 없는 응답을 반환했습니다.",
                    exception
            );
        } catch (HttpServerErrorException exception) {
            throw paymentFailure(
                    PaymentFailureType.UNAVAILABLE,
                    "결제 서버를 일시적으로 사용할 수 없어 결과를 확인 중입니다.",
                    exception
            );
        } catch (ResourceAccessException exception) {
            PaymentFailureType type = containsTimeout(exception)
                    ? PaymentFailureType.TIMEOUT
                    : PaymentFailureType.UNAVAILABLE;
            String message = type == PaymentFailureType.TIMEOUT
                    ? "결제 서버 응답 시간이 초과되어 결과를 확인 중입니다."
                    : "결제 서버에 연결할 수 없어 결과를 확인 중입니다.";
            throw paymentFailure(type, message, exception);
        } catch (RestClientException exception) {
            throw paymentFailure(
                    PaymentFailureType.INVALID_RESPONSE,
                    "결제 서버 응답을 해석할 수 없어 결과를 확인 중입니다.",
                    exception
            );
        }
    }

    private PaymentException paymentFailure(
            PaymentFailureType type,
            String message,
            RestClientException cause
    ) {
        // 응답 본문이나 인증정보는 기록하지 않고 분류와 예외 타입만 진단 로그에 남깁니다.
        log.warn("External payment call failed: type={}, cause={}", type, cause.getClass().getSimpleName());
        return new PaymentException(type, message, cause);
    }

    private boolean containsTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
