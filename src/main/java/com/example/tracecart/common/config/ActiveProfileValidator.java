package com.example.tracecart.common.config;

import java.util.Arrays;
import java.util.Set;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

// 오타나 상충하는 실행 환경 때문에 Fake 결제나 데모 데이터가 잘못 활성화되지 않게 합니다.
@Component
public class ActiveProfileValidator implements SmartInitializingSingleton {

    private static final Set<String> ALLOWED_PROFILES = Set.of("local", "dev", "test", "prod");
    private final Environment environment;

    public ActiveProfileValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        String[] selectedProfiles = environment.getActiveProfiles();
        if (selectedProfiles.length == 0) {
            selectedProfiles = environment.getDefaultProfiles();
        }
        boolean exactlyOneKnownProfile = selectedProfiles.length == 1
                && ALLOWED_PROFILES.contains(selectedProfiles[0]);
        if (!exactlyOneKnownProfile) {
            throw new IllegalStateException(
                    "local, dev, test, prod 중 정확히 하나의 프로파일만 활성화해야 합니다. 현재 값: "
                            + Arrays.toString(selectedProfiles)
            );
        }
    }
}
