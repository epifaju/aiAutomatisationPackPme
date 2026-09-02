package com.aipack.common.api;

import com.aipack.auth.AuthException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.failure(ApiError.of(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> details = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "invalid",
                        (left, right) -> left));
        ApiError error = new ApiError("VALIDATION_ERROR", "Requête invalide", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failure(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        ApiError error = ApiError.of("INTERNAL_ERROR", "Une erreur interne est survenue");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.failure(error));
    }
}
