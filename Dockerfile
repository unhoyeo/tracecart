# Java 21 애플리케이션 실행에 필요한 최소 JRE 이미지를 기반으로 사용합니다.
FROM eclipse-temurin:21-jre

# 컨테이너 안의 이후 명령 기준 폴더를 /app으로 정합니다.
WORKDIR /app
# docker build 때 복사할 Spring Boot JAR 경로이며 기본값은 Gradle 산출물입니다.
ARG JAR_FILE=build/libs/tracecart-0.1.0.jar
# 호스트에서 미리 빌드한 실행 JAR을 컨테이너 내부 app.jar로 복사합니다.
COPY ${JAR_FILE} app.jar

# 애플리케이션이 기본적으로 8080 포트를 사용한다는 메타데이터입니다.
EXPOSE 8080
# 컨테이너가 시작되면 Java로 Spring Boot 실행 JAR을 구동합니다.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
