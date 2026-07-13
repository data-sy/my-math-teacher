package com.mmt.api.exception;

import org.springframework.http.HttpStatus;

/**
 * M7 spec-01 자가진단 경로 전용 예외. ResponseStatusException 은 핸들러가 없어
 * /error 디스패치를 타는데, /error 가 permitAll 이 아니라 익명 요청(permitAll
 * 엔드포인트)의 4xx 가 전부 401 로 마스킹된다 — 익명 preview/next 의 400·429 가
 * 학생에게 401 로 보이면 게이트 UX(F-1)가 깨지므로 전용 타입 + 직접 핸들러로 우회.
 * (구 경로의 동일 마스킹은 무변경 보존 대상이라 손대지 않음 — 완성물 리뷰에 보고.)
 */
public class DiagnosisException extends RuntimeException {

    private final HttpStatus status;

    public DiagnosisException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
