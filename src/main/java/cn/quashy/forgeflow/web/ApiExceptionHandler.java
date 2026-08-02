package cn.quashy.forgeflow.web;

import cn.quashy.forgeflow.service.DemoException;
import cn.quashy.forgeflow.web.ApiModels.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DemoException.class)
    public ResponseEntity<ApiError> handleDemoException(DemoException exception) {
        return ResponseEntity.status(exception.getStatus())
            .body(new ApiError(exception.getCode(), exception.getMessage(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage())
            .orElse("提交数据不符合要求");
        return ResponseEntity.badRequest()
            .body(new ApiError("VALIDATION_ERROR", message, Instant.now()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiError("WORKFLOW_CONFLICT", "数据刚刚被其他人更新，请刷新后重试", Instant.now()));
    }
}
