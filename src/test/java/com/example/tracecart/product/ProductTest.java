package com.example.tracecart.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

// Spring 컨테이너와 데이터베이스 없이 Product 도메인 규칙만 검증하는 순수 단위 테스트입니다.
class ProductTest {

    @Test
    void decreasesStockWhenEnoughStockExists() {
        Product product = new Product("Keyboard", new BigDecimal("10000.00"), 5);

        product.decreaseStock(2);

        assertThat(product.getStock()).isEqualTo(3);
    }

    @Test
    void rejectsOrderWhenStockIsInsufficient() {
        Product product = new Product("Keyboard", new BigDecimal("10000.00"), 1);

        assertThatThrownBy(() -> product.decreaseStock(2))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("재고");
        assertThat(product.getStock()).isEqualTo(1);
    }

    @Test
    void restoresPreviouslyDecreasedStock() {
        Product product = new Product("Keyboard", new BigDecimal("10000.00"), 5);
        product.decreaseStock(2);

        product.restoreStock(2);

        assertThat(product.getStock()).isEqualTo(5);
    }

    @Test
    void rejectsZeroOrNegativeOrderQuantityWithoutChangingStock() {
        Product product = new Product("Keyboard", new BigDecimal("10000.00"), 5);

        assertThatThrownBy(() -> product.decreaseStock(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> product.decreaseStock(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(product.getStock()).isEqualTo(5);
    }

    @Test
    void rejectsInvalidProductValuesAtConstructionTime() {
        assertThatThrownBy(() -> new Product(" ", new BigDecimal("10000.00"), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이름");
        assertThatThrownBy(() -> new Product("Keyboard", BigDecimal.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("가격");
        assertThatThrownBy(() -> new Product("Keyboard", new BigDecimal("1.001"), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("가격");
        assertThatThrownBy(() -> new Product("K".repeat(101), new BigDecimal("10000.00"), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이름");
        assertThatThrownBy(() -> new Product("Keyboard", new BigDecimal("10000.00"), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("재고");
    }

    @Test
    void rejectsInvalidRestoreQuantityWithoutChangingStock() {
        Product product = new Product("Keyboard", new BigDecimal("10000.00"), 5);

        assertThatThrownBy(() -> product.restoreStock(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("복원 수량");
        assertThat(product.getStock()).isEqualTo(5);
    }
}
