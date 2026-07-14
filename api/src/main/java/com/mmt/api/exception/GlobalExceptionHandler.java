package com.mmt.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    // ControllerAdvice가 아니라 RestControllerAdvice이므로 ResponseEntityExceptionHandler 상속받을 필요 없음

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    @ExceptionHandler(DuplicateMemberException.class)
    public ResponseEntity<String> handleDuplicateMemberException(DuplicateMemberException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(NotFoundMemberException.class)
    public ResponseEntity<String> handleNotFoundMemberException(NotFoundMemberException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<String> handleUnauthorizedException(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }

    // M7 spec-01: 자가진단 경로 전용 — ResponseStatusException 은 /error 디스패치를 타
    // 익명(permitAll) 요청의 4xx 가 401 로 마스킹되므로 전용 타입을 직접 응답으로 처리
    // (구 경로 응답 shape 무변경 — 이 핸들러는 DiagnosisException 만 잡는다)
    @ExceptionHandler(DiagnosisException.class)
    public ResponseEntity<String> handleDiagnosisException(DiagnosisException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ex.getMessage());
    }

}
