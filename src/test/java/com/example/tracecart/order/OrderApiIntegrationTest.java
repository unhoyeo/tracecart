package com.example.tracecart.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.tracecart.common.logging.TraceIdFilter;
import com.example.tracecart.order.domain.IdempotencyKey;
import com.example.tracecart.order.domain.PurchaseOrderRepository;
import com.example.tracecart.payment.PaymentScenarioResolver;
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

// 주문 API의 상태 코드, 멱등성, 데모 시나리오 헤더와 JSON 계약을 검증합니다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class OrderApiIntegrationTest {

    @Autowired WebApplicationContext context;
    @Autowired TraceIdFilter traceIdFilter;
    @Autowired ProductRepository productRepository;
    @Autowired PurchaseOrderRepository orderRepository;

    private MockMvc mockMvc;
    private Long productId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(traceIdFilter).build();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        productId = productRepository.save(new Product("Hub", new BigDecimal("50000.00"), 10)).getId();
    }

    @Test
    void successReturnsCreatedOrderAndTraceHeader() throws Exception {
        mockMvc.perform(orderPost("idem-api-success-0001", "SUCCESS", orderBody(productId, 2))
                        .header(TraceIdFilter.TRACE_HEADER, "demo-trace-12345678"))
                .andExpect(status().isCreated())
                .andExpect(header().string(TraceIdFilter.TRACE_HEADER, "demo-trace-12345678"))
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/orders/\\d+")))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.totalPrice").value(100000.00));
    }

    @Test
    void explicitDeclineReturnsUnprocessableContentAndRestoresStock() throws Exception {
        mockMvc.perform(orderPost("idem-api-decline-0001", "FAILURE", orderBody(productId, 2)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value("PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.failureReason").value("결제가 거절되었습니다."));

        assertThatCurrentStockIs(10);
    }

    @Test
    void timeoutReturnsAcceptedUnknownOrderAndKeepsReservation() throws Exception {
        mockMvc.perform(orderPost("idem-api-timeout-0001", "TIMEOUT", orderBody(productId, 2)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PAYMENT_UNKNOWN"))
                .andExpect(jsonPath("$.failureReason").value(
                        "결제 서버 응답 시간이 초과되어 결과를 확인 중입니다."
                ));

        assertThatCurrentStockIs(8);
    }

    @Test
    void idempotentReplayReturnsOkAndDoesNotDecreaseStockTwice() throws Exception {
        String key = "idem-api-replay-0001";
        mockMvc.perform(orderPost(key, "SUCCESS", orderBody(productId, 2)))
                .andExpect(status().isCreated());

        mockMvc.perform(orderPost(key, "SUCCESS", orderBody(productId, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        assertThatCurrentStockIs(8);
        org.assertj.core.api.Assertions.assertThat(orderRepository.count()).isOne();
    }

    @Test
    void reusedIdempotencyKeyWithDifferentPayloadReturnsConflict() throws Exception {
        String key = "idem-api-conflict-0001";
        mockMvc.perform(orderPost(key, "SUCCESS", orderBody(productId, 1)))
                .andExpect(status().isCreated());

        mockMvc.perform(orderPost(key, "SUCCESS", orderBody(productId, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void missingIdempotencyKeyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(productId, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"));
    }

    @Test
    void invalidIdempotencyKeyReturnsBadRequest() throws Exception {
        mockMvc.perform(orderPost("short", "SUCCESS", orderBody(productId, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void invalidQuantityReturnsBadRequest() throws Exception {
        mockMvc.perform(orderPost("idem-api-quantity-0001", "SUCCESS", orderBody(productId, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void quantityAboveLimitReturnsBadRequest() throws Exception {
        mockMvc.perform(orderPost("idem-api-maximum-0001", "SUCCESS", orderBody(productId, 101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void nonPositiveProductIdReturnsBadRequest() throws Exception {
        mockMvc.perform(orderPost("idem-api-product-0001", "SUCCESS", orderBody(0L, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unknownDemoScenarioReturnsBadRequest() throws Exception {
        mockMvc.perform(orderPost("idem-api-scenario-0001", "UNKNOWN", orderBody(productId, 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYMENT_SCENARIO"));
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(orderPost("idem-api-json-0001", "SUCCESS", "{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unknownProductReturnsNotFound() throws Exception {
        mockMvc.perform(orderPost("idem-api-missing-0001", "SUCCESS", orderBody(999999L, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void insufficientStockReturnsConflict() throws Exception {
        mockMvc.perform(orderPost("idem-api-stock-0001", "SUCCESS", orderBody(productId, 11)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        assertThatCurrentStockIs(10);
    }

    @Test
    void unknownOrderReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/orders/{orderId}", 999999L)
                        .header(TraceIdFilter.TRACE_HEADER, "order-trace-123456"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder orderPost(
            String idempotencyKey,
            String scenario,
            String body
    ) {
        return post("/api/orders")
                .header(IdempotencyKey.HEADER, idempotencyKey)
                .header(PaymentScenarioResolver.HEADER, scenario)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String orderBody(Long requestedProductId, int quantity) {
        return """
                {
                  "userId": "portfolio-user",
                  "productId": %d,
                  "quantity": %d
                }
                """.formatted(requestedProductId, quantity);
    }

    private void assertThatCurrentStockIs(int expectedStock) {
        org.assertj.core.api.Assertions.assertThat(
                productRepository.findById(productId).orElseThrow().getStock()
        ).isEqualTo(expectedStock);
    }
}
