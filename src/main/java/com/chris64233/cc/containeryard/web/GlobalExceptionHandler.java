package com.chris64233.cc.containeryard.web;

import com.chris64233.cc.containeryard.service.NotFoundException;
import com.chris64233.cc.containeryard.service.PlanValidationException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PlanValidationException.class)
    public ResponseEntity<Map<String, String>> validation(PlanValidationException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("error", "PLAN_VALIDATION_FAILED", "message", ex.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
    }

    @ExceptionHandler({ConcurrencyFailureException.class, OptimisticLockException.class,
            PessimisticLockException.class})
    public ResponseEntity<Map<String, String>> conflict(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "CONCURRENT_MODIFICATION",
                        "message", "相关堆栈已被并发修改，请重新提交计划"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> integrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "DATA_INTEGRITY_VIOLATION",
                        "message", "违反唯一性或完整性约束"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> badRequest(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "BAD_REQUEST", "message", "请求参数不合法"));
    }
}
