package com.example.tracecart.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tracecart.common.logging.TraceIdFilter;
import com.example.tracecart.order.domain.PurchaseOrderRepository;
import com.example.tracecart.product.Product;
import com.example.tracecart.product.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// HTTP 직전 계층까지 포함해 주문 생성 API의 상태, 헤더, JSON을 검증합니다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
// 외부 시스템 없이 H2와 Fake 결제를 사용하는 test 프로파일을 켭니다.
@ActiveProfiles("test")
class OrderApiIntegrationTest {

    // Controller와 예외 처리기 등이 등록된 웹 애플리케이션 컨텍스트입니다.
    @Autowired WebApplicationContext context;
    // MockMvc 요청에도 실제 추적 필터를 명시적으로 적용하기 위한 빈입니다.
    @Autowired TraceIdFilter traceIdFilter;
    // 테스트 상품 준비에 사용할 저장소입니다.
    @Autowired ProductRepository productRepository;
    // 테스트 주문 정리에 사용할 저장소입니다.
    @Autowired PurchaseOrderRepository orderRepository;

    // 실제 포트를 열지 않고 Spring MVC 요청을 실행하는 테스트 도구입니다.
    private MockMvc mockMvc;
    // 각 테스트에서 요청 JSON에 넣을 저장된 상품 ID입니다.
    private Long productId;

    // 테스트 실행 전 MockMvc와 데이터베이스 상태를 준비합니다.
    @BeforeEach
    void setUp() {
        // 실제 웹 컨텍스트에 TraceIdFilter를 추가해 MockMvc를 만듭니다.
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(traceIdFilter).build();
        // 외래 키 순서를 고려해 주문부터 삭제합니다.
        orderRepository.deleteAll();
        // 이전 테스트의 상품을 삭제합니다.
        productRepository.deleteAll();
        // 주문 API가 사용할 가격 5만원, 재고 10개의 상품을 저장합니다.
        productId = productRepository.save(new Product("Hub", new BigDecimal("50000.00"), 10)).getId();
    }

    // 성공 주문 API가 201, trace 헤더, 올바른 JSON을 반환하는지 검증합니다.
    @Test
    void createsOrderAndReturnsTraceHeader() throws Exception {
        String body = orderBody(productId, 2, "SUCCESS");

        // When: traceId 헤더와 JSON 본문을 넣어 주문 생성 API를 호출합니다.
        mockMvc.perform(post("/api/orders")
                        // 서버가 그대로 사용해야 할 안전한 추적 ID를 보냅니다.
                        .header(TraceIdFilter.TRACE_HEADER, "demo-trace-12345678")
                        // 요청 본문이 JSON임을 알립니다.
                        .contentType(MediaType.APPLICATION_JSON)
                        // 위에서 만든 JSON 문자열을 HTTP 본문에 넣습니다.
                        .content(body))
                // Then: 새 주문이 생성됐으므로 HTTP 201이어야 합니다.
                .andExpect(status().isCreated())
                // Then: 응답에도 요청과 같은 traceId가 있어야 합니다.
                .andExpect(header().string(TraceIdFilter.TRACE_HEADER, "demo-trace-12345678"))
                // Then: 응답 JSON의 주문 상태가 PAID여야 합니다.
                .andExpect(jsonPath("$.status").value("PAID"))
                // Then: 5만원짜리 두 개의 총액이 10만원이어야 합니다.
                .andExpect(jsonPath("$.totalPrice").value(100000.00));
    }

    @Test
    void returnsFailedOrderAndRestoresStockForRejectedPayment() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(TraceIdFilter.TRACE_HEADER, "failure-trace-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId, 2, "FAILURE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$.failureReason").value("결제가 거절되었습니다."));

        assertThatCurrentStockIs(10);
    }

    @Test
    void returnsFailedOrderAndRestoresStockForTimeout() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(TraceIdFilter.TRACE_HEADER, "timeout-trace-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId, 2, "TIMEOUT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAYMENT_FAILED"))
                .andExpect(jsonPath("$.failureReason").value("결제 서버 응답 시간이 초과되었습니다."));

        assertThatCurrentStockIs(10);
    }

    @Test
    void returnsBadRequestForInvalidQuantity() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(TraceIdFilter.TRACE_HEADER, "invalid-trace-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId, 0, "SUCCESS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.traceId").value("invalid-trace-1234"));
    }

    @Test
    void returnsBadRequestWhenQuantityExceedsRequestLimit() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(TraceIdFilter.TRACE_HEADER, "maximum-trace-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId, 101, "SUCCESS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.traceId").value("maximum-trace-1234"));
    }

    @Test
    void returnsBadRequestForNonPositiveProductId() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(TraceIdFilter.TRACE_HEADER, "product-id-trace-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(0L, 1, "SUCCESS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.traceId").value("product-id-trace-1234"));
    }

    @Test
    void returnsBadRequestForMissingPaymentScenario() throws Exception {
        String body = """
                {
                  "userId": "portfolio-user",
                  "productId": %d,
                  "quantity": 1
                }
                """.formatted(productId);

        mockMvc.perform(post("/api/orders")
                        .header(TraceIdFilter.TRACE_HEADER, "scenario-trace-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.traceId").value("scenario-trace-1234"));
    }

    @Test
    void returnsBadRequestForUnknownPaymentScenario() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(TraceIdFilter.TRACE_HEADER, "enum-trace-123456")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId, 1, "UNKNOWN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.traceId").value("enum-trace-123456"));
    }

    @Test
    void returnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(TraceIdFilter.TRACE_HEADER, "json-trace-1234567")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.traceId").value("json-trace-1234567"));
    }

    @Test
    void returnsNotFoundForUnknownProduct() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(TraceIdFilter.TRACE_HEADER, "missing-trace-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(999999L, 1, "SUCCESS")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("missing-trace-1234"));
    }

    @Test
    void returnsConflictWhenStockIsInsufficient() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(TraceIdFilter.TRACE_HEADER, "stock-trace-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId, 11, "SUCCESS")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.traceId").value("stock-trace-1234"));

        assertThatCurrentStockIs(10);
    }

    @Test
    void returnsNotFoundWhenReadingUnknownOrder() throws Exception {
        mockMvc.perform(get("/api/orders/{orderId}", 999999L)
                        .header(TraceIdFilter.TRACE_HEADER, "order-trace-123456"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("order-trace-123456"));
    }

    private String orderBody(Long requestedProductId, int quantity, String scenario) {
        return """
                {
                  "userId": "portfolio-user",
                  "productId": %d,
                  "quantity": %d,
                  "paymentScenario": "%s"
                }
                """.formatted(requestedProductId, quantity, scenario);
    }

    private void assertThatCurrentStockIs(int expectedStock) {
        org.assertj.core.api.Assertions.assertThat(
                productRepository.findById(productId).orElseThrow().getStock()
        ).isEqualTo(expectedStock);
    }
}
