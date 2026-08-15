package com.example.tracecart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// 자동 설정, 컴포넌트 탐색, Java 설정 기능을 한 번에 활성화하는 애플리케이션 시작점입니다.
@SpringBootApplication
public class TraceCartApplication {

    // JVM이 애플리케이션을 시작할 때 가장 먼저 호출하는 메서드입니다.
    public static void main(String[] args) {
        // Spring 컨테이너를 만들고 내장 Tomcat을 시작하며 모든 빈을 조립합니다.
        SpringApplication.run(TraceCartApplication.class, args);
    }
}
