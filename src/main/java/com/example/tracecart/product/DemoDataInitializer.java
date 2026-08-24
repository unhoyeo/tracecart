package com.example.tracecart.product;

import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
// 운영에서는 샘플 데이터가 생기지 않도록 local과 dev에서만 활성화합니다.
@Profile("(local | dev) & !prod")
// ApplicationRunner는 Spring 컨테이너 준비가 끝난 직후 실행할 작업을 표현합니다.
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private final ProductRepository productRepository;

    public DemoDataInitializer(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // 애플리케이션 기동이 끝나면 Spring이 호출합니다.
    @Override
    public void run(ApplicationArguments args) {
        // 이미 상품이 하나라도 있으면 중복 샘플 데이터를 만들지 않습니다.
        if (productRepository.count() > 0) {
            return;
        }
        // 데모 API를 바로 호출할 수 있도록 상품 세 개를 한 번에 저장합니다.
        productRepository.saveAll(List.of(
                new Product("Mechanical Keyboard", new BigDecimal("129000.00"), 20),
                new Product("4K Monitor", new BigDecimal("489000.00"), 10),
                new Product("USB-C Hub", new BigDecimal("69000.00"), 50)
        ));
        log.info("Demo products initialized");
    }
}
