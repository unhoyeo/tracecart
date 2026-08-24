package com.example.tracecart.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.tracecart.order.application.CreateOrderCommand;
import com.example.tracecart.order.application.OrderService;
import com.example.tracecart.order.domain.IdempotencyKey;
import com.example.tracecart.order.domain.OrderQuantity;
import com.example.tracecart.order.domain.OrderUserId;
import com.example.tracecart.order.domain.PurchaseOrderRepository;
import com.example.tracecart.payment.PaymentClient;
import com.example.tracecart.payment.PaymentCommand;
import com.example.tracecart.payment.PaymentResult;
import com.example.tracecart.payment.PaymentScenario;
import com.example.tracecart.product.Product;
import com.example.tracecart.product.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Docker가 있으면 실제 PostgreSQL에서 Flyway, 멱등성, 낙관적 잠금을 검증합니다.
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class PostgreSqlOrderIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ProductRepository productRepository;
    @Autowired PurchaseOrderRepository orderRepository;
    @Autowired OrderService orderService;
    @Autowired TransactionTemplate transactionTemplate;

    @MockitoBean PaymentClient paymentClient;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        when(paymentClient.pay(any(PaymentCommand.class)))
                .thenAnswer(invocation -> new PaymentResult(
                        "pg-" + invocation.<PaymentCommand>getArgument(0).orderId()
                ));
    }

    @Test
    void runsFlywaySchemaOnRealPostgreSqlAndReusesIdempotentPayment() {
        String databaseVersion = jdbcTemplate.queryForObject("select version()", String.class);
        Product product = productRepository.save(
                new Product("PostgreSQL Keyboard", new BigDecimal("10000.00"), 3)
        );
        CreateOrderCommand command = command("idem-postgres-0001", product.getId());

        Long firstOrderId = orderService.create(command).order().id();
        Long replayOrderId = orderService.create(command).order().id();

        assertThat(databaseVersion).contains("PostgreSQL");
        assertThat(replayOrderId).isEqualTo(firstOrderId);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStock()).isEqualTo(2);
        verify(paymentClient, times(1)).pay(any(PaymentCommand.class));
    }

    @Test
    void optimisticLockRejectsOneOfTwoConcurrentPostgreSqlUpdates() throws Exception {
        Long productId = productRepository.saveAndFlush(
                new Product("Last PostgreSQL Keyboard", new BigDecimal("10000.00"), 1)
        ).getId();
        CyclicBarrier bothLoaded = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Void> decrease = () -> {
            transactionTemplate.executeWithoutResult(status -> {
                Product product = productRepository.findById(productId).orElseThrow();
                product.decreaseStock(1);
                await(bothLoaded);
            });
            return null;
        };

        try {
            List<Future<Void>> futures = List.of(executor.submit(decrease), executor.submit(decrease));
            List<Throwable> failures = collectFailures(futures);

            assertThat(failures).hasSize(1);
            assertThat(failures.getFirst()).isInstanceOf(ObjectOptimisticLockingFailureException.class);
            assertThat(productRepository.findById(productId).orElseThrow().getStock()).isZero();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    private CreateOrderCommand command(String key, Long productId) {
        return new CreateOrderCommand(
                new IdempotencyKey(key),
                new OrderUserId("postgres-user"),
                productId,
                new OrderQuantity(1),
                PaymentScenario.SUCCESS
        );
    }

    private List<Throwable> collectFailures(List<Future<Void>> futures) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<Void> future : futures) {
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                failures.add(exception.getCause());
            } catch (java.util.concurrent.TimeoutException exception) {
                throw new AssertionError("PostgreSQL 동시성 테스트가 제한 시간 안에 끝나지 않았습니다.", exception);
            }
        }
        return failures;
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("PostgreSQL 동시성 테스트가 중단됐습니다.", exception);
        } catch (java.util.concurrent.BrokenBarrierException | java.util.concurrent.TimeoutException exception) {
            throw new AssertionError("두 PostgreSQL 트랜잭션이 동시에 상품을 읽지 못했습니다.", exception);
        }
    }
}
