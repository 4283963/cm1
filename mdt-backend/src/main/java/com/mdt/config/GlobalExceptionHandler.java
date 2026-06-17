package com.mdt.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            ObjectOptimisticLockingFailureException e) {
        log.warn("乐观锁冲突: entity={}, id={}, msg={}",
                e.getPersistentClassName(), e.getIdentifier(), e.getMessage());
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "操作冲突，数据已被他人修改，请刷新后重试",
                "OPTIMISTIC_LOCK"
        );
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLockGeneric(
            OptimisticLockingFailureException e) {
        log.warn("乐观锁冲突: {}", e.getMessage());
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "操作冲突，请稍后重试",
                "OPTIMISTIC_LOCK"
        );
    }

    @ExceptionHandler({
            PessimisticLockingFailureException.class,
            CannotAcquireLockException.class,
            DeadlockLoserDataAccessException.class
    })
    public ResponseEntity<Map<String, Object>> handlePessimisticLock(DataAccessException e) {
        log.error("数据库锁异常: {}", e.getMessage(), e);
        return buildErrorResponse(
                HttpStatus.CONFLICT,
                "系统繁忙，多人同时操作冲突，请稍后重试",
                "DB_LOCK"
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);

        if (msg.contains("unique") || msg.contains("uk_") || msg.contains("duplicate")) {
            log.warn("唯一约束冲突 (幂等重复提交): {}", e.getMessage());
            return buildErrorResponse(
                    HttpStatus.CONFLICT,
                    "操作已被处理，请勿重复提交",
                    "UNIQUE_CONSTRAINT"
            );
        }

        if (msg.contains("foreign key") || msg.contains("fk_")) {
            log.warn("外键约束冲突: {}", e.getMessage());
            return buildErrorResponse(
                    HttpStatus.BAD_REQUEST,
                    "关联数据不存在，请检查参数",
                    "FK_CONSTRAINT"
            );
        }

        if (msg.contains("null") || msg.contains("not null")) {
            log.warn("非空约束冲突: {}", e.getMessage());
            return buildErrorResponse(
                    HttpStatus.BAD_REQUEST,
                    "必填字段缺失",
                    "NOT_NULL"
            );
        }

        log.error("数据完整性异常: {}", e.getMessage(), e);
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "数据校验失败，请检查输入",
                "DATA_INTEGRITY"
        );
    }

    @ExceptionHandler(CannotCreateTransactionException.class)
    public ResponseEntity<Map<String, Object>> handleTransactionCreation(
            CannotCreateTransactionException e) {
        log.error("无法创建事务连接: {}", e.getMessage(), e);
        return buildErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "数据库连接繁忙，请稍后重试",
                "TX_CREATION"
        );
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: {}", e.getMessage(), e);
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                e.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "参数校验失败");
        result.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                e.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "系统内部错误，请稍后重试"
        );
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status,
                                                                    String message) {
        return buildErrorResponse(status, message, null);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status,
                                                                    String message,
                                                                    String code) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", message);
        if (code != null) {
            result.put("code", code);
        }
        return ResponseEntity.status(status).body(result);
    }
}
