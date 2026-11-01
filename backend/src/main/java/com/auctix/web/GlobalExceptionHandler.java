package com.auctix.web;

import com.auctix.exception.ApiError;
import com.auctix.exception.ApiException;
import com.auctix.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns every failure into the same {@link ApiError} shape with a stable error code,
 * so the frontend can show a friendly message without parsing stack traces.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(ex.getCode(), ex.getMessage(), request.getRequestURI(), ex.getDetails());
        return ResponseEntity.status(ex.getCode().getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> fields.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        String message = fields.isEmpty()
                ? ErrorCode.VALIDATION_ERROR.getDefaultMessage()
                : String.valueOf(fields.values().iterator().next());
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiError.of(ErrorCode.VALIDATION_ERROR, message, request.getRequestURI(), fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiError.of(ErrorCode.VALIDATION_ERROR,
                        "The request could not be read: " + rootMessage(ex),
                        request.getRequestURI(), Map.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.USER_NOT_AUTHORIZED.getStatus())
                .body(ApiError.of(ErrorCode.USER_NOT_AUTHORIZED,
                        ErrorCode.USER_NOT_AUTHORIZED.getDefaultMessage(), request.getRequestURI(), Map.of()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.UNAUTHENTICATED.getStatus())
                .body(ApiError.of(ErrorCode.UNAUTHENTICATED,
                        ErrorCode.UNAUTHENTICATED.getDefaultMessage(), request.getRequestURI(), Map.of()));
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiError> handleConcurrency(ConcurrencyFailureException ex,
                                                      HttpServletRequest request) {
        return ResponseEntity.status(ErrorCode.CONCURRENT_UPDATE.getStatus())
                .body(ApiError.of(ErrorCode.CONCURRENT_UPDATE,
                        ErrorCode.CONCURRENT_UPDATE.getDefaultMessage(), request.getRequestURI(), Map.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex,
                                                    HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), rootMessage(ex));
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiError.of(ErrorCode.VALIDATION_ERROR,
                        "That operation conflicts with existing data.", request.getRequestURI(), Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiError.of(ErrorCode.INTERNAL_ERROR,
                        ErrorCode.INTERNAL_ERROR.getDefaultMessage(), request.getRequestURI(), Map.of()));
    }

    private String rootMessage(Throwable ex) {
        Throwable cursor = ex;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null ? ex.getClass().getSimpleName() : message;
    }
}
