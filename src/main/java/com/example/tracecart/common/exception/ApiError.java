package com.example.tracecart.common.exception;

import java.time.Instant;

// 모든 API 오류가 같은 JSON 구조를 사용하도록 정의한 불변 응답 객체입니다.
public record ApiError(
        // 서버 기준으로 오류가 발생한 정확한 시각입니다.
        Instant timestamp,
        // 400, 404, 500 같은 HTTP 숫자 상태 코드입니다.
        int status,
        // 클라이언트 코드가 분기 처리할 안정적인 업무 오류 코드입니다.
        String code,
        // 사람이 읽을 수 있는 오류 설명입니다.
        String message,
        // 사용자가 서버 로그를 찾을 때 제시할 수 있는 요청 추적 ID입니다.
        String traceId
) {
}
