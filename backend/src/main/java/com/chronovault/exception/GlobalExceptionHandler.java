package com.chronovault.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, ex.getMessage()));
    }

    @ExceptionHandler(RollbackFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleRollbackFailed(RollbackFailedException ex) {
        log.error("Rollback operation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.ROLLBACK_FAILED));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, "邮箱或密码错误"));
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(UsernameNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(field, message);
        });
        ApiResponse<Map<String, String>> response = new ApiResponse<>(400, "参数验证失败", errors, LocalDateTime.now());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, ex.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "缺少必需参数: " + ex.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "参数类型错误: " + ex.getName() + " 应为 " + (ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "正确类型")));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        log.warn("数据库约束冲突: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(409, "数据冲突：违反数据库约束，请检查输入数据"));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "请求体格式错误: " + (ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "无法解析请求体")));
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(jakarta.validation.ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String field = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            errors.put(field, message);
        });
        ApiResponse<Map<String, String>> response = new ApiResponse<>(400, "参数验证失败", errors, LocalDateTime.now());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("未处理的异常: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "服务器内部错误"));
    }

    public record ApiResponse<T>(int code, String message, T data, LocalDateTime timestamp) {
        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(200, "success", data, LocalDateTime.now());
        }

        public static <T> ApiResponse<T> success(String message, T data) {
            return new ApiResponse<>(200, message, data, LocalDateTime.now());
        }

        public static ApiResponse<Void> successMsg(String message) {
            return new ApiResponse<>(200, message, null, LocalDateTime.now());
        }

        public static ApiResponse<Void> error(int code, String message) {
            return new ApiResponse<>(code, message, null, LocalDateTime.now());
        }

        /**
         * Create error response using unified ErrorCode enum.
         */
        public static ApiResponse<Void> error(ErrorCode errorCode) {
            return new ApiResponse<>(errorCode.getCode(), errorCode.getDefaultMessage(), null, LocalDateTime.now());
        }

        /**
         * Create error response using unified ErrorCode with custom message.
         */
        public static ApiResponse<Void> error(ErrorCode errorCode, String customMessage) {
            return new ApiResponse<>(errorCode.getCode(), customMessage, null, LocalDateTime.now());
        }

        public static <T> ApiResponse<PageResponse<T>> successPage(List<T> content, int page, int size, long total) {
            return new ApiResponse<>(200, "success", new PageResponse<>(content, page, size, total), LocalDateTime.now());
        }
    }

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public PageResponse(List<T> content, int page, int size, long totalElements) {
            this(content, page, size, totalElements, (int) Math.ceil((double) totalElements / size));
        }
    }
}
