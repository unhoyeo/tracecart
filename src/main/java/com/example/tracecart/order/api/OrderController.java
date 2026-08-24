package com.example.tracecart.order.api;

import com.example.tracecart.order.application.CreateOrderCommand;
import com.example.tracecart.order.application.CreateOrderResult;
import com.example.tracecart.order.application.OrderService;
import com.example.tracecart.order.domain.IdempotencyKey;
import com.example.tracecart.order.domain.OrderQuantity;
import com.example.tracecart.order.domain.OrderStatus;
import com.example.tracecart.order.domain.OrderUserId;
import com.example.tracecart.payment.PaymentScenario;
import com.example.tracecart.payment.PaymentScenarioResolver;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final PaymentScenarioResolver paymentScenarioResolver;

    public OrderController(OrderService orderService, PaymentScenarioResolver paymentScenarioResolver) {
        this.orderService = orderService;
        this.paymentScenarioResolver = paymentScenarioResolver;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestHeader(IdempotencyKey.HEADER) String idempotencyKey,
            @RequestHeader(name = PaymentScenarioResolver.HEADER, required = false) String scenarioHeader,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        PaymentScenario scenario = paymentScenarioResolver.resolve(scenarioHeader);
        CreateOrderCommand command = new CreateOrderCommand(
                new IdempotencyKey(idempotencyKey),
                new OrderUserId(request.userId()),
                request.productId(),
                new OrderQuantity(request.quantity()),
                scenario
        );
        CreateOrderResult result = orderService.create(command);
        OrderResponse response = OrderResponse.from(result.order());
        HttpStatus status = responseStatus(result);
        return ResponseEntity.status(status)
                .location(URI.create("/api/orders/" + response.id()))
                .body(response);
    }

    @GetMapping("/{orderId}")
    public OrderResponse findById(@PathVariable Long orderId) {
        return OrderResponse.from(orderService.findById(orderId));
    }

    private HttpStatus responseStatus(CreateOrderResult result) {
        OrderStatus status = result.order().status();
        if (status == OrderStatus.PAID) {
            return result.newlyCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        }
        if (status == OrderStatus.PAYMENT_DECLINED) {
            return HttpStatus.UNPROCESSABLE_CONTENT;
        }
        return HttpStatus.ACCEPTED;
    }
}
