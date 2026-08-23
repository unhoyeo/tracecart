package com.example.tracecart.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

// 두 트랜잭션이 같은 버전의 재고를 수정할 때 한쪽이 낙관적 잠금으로 거절되는지 검증합니다.
@SpringBootTest
@ActiveProfiles("test")
class ProductOptimisticLockIntegrationTest {

    @Autowired ProductRepository productRepository;
    @Autowired TransactionTemplate transactionTemplate;

    private Long productId;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        productId = productRepository.saveAndFlush(
                new Product("Last Keyboard", new BigDecimal("10000.00"), 1)
        ).getId();
    }

    @Test
    void onlyOneConcurrentStockUpdateCommits() throws Exception {
        CyclicBarrier bothLoaded = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Void> decreaseOne = () -> {
            transactionTemplate.executeWithoutResult(status -> {
                Product product = productRepository.findById(productId).orElseThrow();
                product.decreaseStock(1);
                await(bothLoaded);
            });
            return null;
        };

        try {
            List<Future<Void>> futures = List.of(
                    executor.submit(decreaseOne),
                    executor.submit(decreaseOne)
            );
            List<Throwable> failures = collectFailures(futures);

            assertThat(failures).hasSize(1);
            // Spring 트랜잭션 경계가 Hibernate의 내부 예외를 이식 가능한 Spring 예외로 변환합니다.
            assertThat(failures.getFirst())
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);
            assertThat(productRepository.findById(productId).orElseThrow().getStock()).isZero();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    private List<Throwable> collectFailures(List<Future<Void>> futures) throws InterruptedException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<Void> future : futures) {
            try {
                future.get(3, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                failures.add(exception.getCause());
            } catch (java.util.concurrent.TimeoutException exception) {
                throw new AssertionError("동시성 테스트가 제한 시간 안에 끝나지 않았습니다.", exception);
            }
        }
        return failures;
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("동시성 테스트가 중단됐습니다.", exception);
        } catch (BrokenBarrierException | java.util.concurrent.TimeoutException exception) {
            throw new AssertionError("두 트랜잭션이 동시에 엔티티를 읽지 못했습니다.", exception);
        }
    }

}
