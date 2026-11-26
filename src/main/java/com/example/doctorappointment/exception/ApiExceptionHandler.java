package com.example.doctorappointment.exception;

import com.example.doctorappointment.dto.response.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.io.IOException;
import java.time.Clock;
import java.util.TreeMap;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ApiExceptionHandler {
    private final Clock clock;
    private final ObjectMapper mapper;

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleBusinessError(ApiException ex, HttpServletRequest request) {
        return error(request, ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var fields = new TreeMap<String, String>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ApiError(clock.instant(), status.value(),
                "VALIDATION_ERROR", "Request validation failed.", request.getRequestURI(), fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class})
    ResponseEntity<ApiError> handleInvalidRequest(Exception ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request data.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleDenied(AccessDeniedException ex, HttpServletRequest request) {
        return error(request, HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this operation.");
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<ApiError> handleConcurrentChange(ConcurrencyFailureException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "A concurrent update occurred. Retry the request.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataConflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, "DATA_CONFLICT", "The request conflicts with existing data.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse frameworkError) {
            HttpStatus status = HttpStatus.valueOf(frameworkError.getStatusCode().value());
            return error(request, status, status.name(), status.getReasonPhrase());
        }
        log.error("Unexpected {} failure of type {}", request.getMethod(), ex.getClass().getName());
        return error(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), body(request, status, code, message));
    }

    private ResponseEntity<ApiError> error(HttpServletRequest request, HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(body(request, status, code, message));
    }

    private ApiError body(HttpServletRequest request, HttpStatus status, String code, String message) {
        return new ApiError(clock.instant(), status.value(), code, message, request.getRequestURI());
    }
}
