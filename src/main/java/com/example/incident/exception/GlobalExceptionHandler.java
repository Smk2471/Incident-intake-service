package com.example.incident.exception;

import com.example.incident.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Centralized translation of exceptions into the ErrorResponse envelope so
 * every failure case -- validation, not-found, illegal state transition,
 * malformed JSON, or unexpected server error -- has the same machine-readable
 * shape ({code, message, status, path, correlationId, fieldErrors}).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();

        log.warn("event=validation_failed path={} fieldErrors={} correlationId={}",
                request.getRequestURI(), fieldErrors, MDC.get("correlationId"));

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "One or more fields failed validation",
                request.getRequestURI(),
                MDC.get("correlationId"),
                fieldErrors
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("event=malformed_request_body path={} correlationId={} detail={}",
                request.getRequestURI(), MDC.get("correlationId"), ex.getMessage());

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "MALFORMED_REQUEST_BODY",
                "Request body is missing or could not be parsed as JSON",
                request.getRequestURI(),
                MDC.get("correlationId")
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IncidentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(IncidentNotFoundException ex, HttpServletRequest request) {
        log.info("event=incident_not_found path={} correlationId={} detail={}",
                request.getRequestURI(), MDC.get("correlationId"), ex.getMessage());

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                "INCIDENT_NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI(),
                MDC.get("correlationId")
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidStatusTransitionException ex, HttpServletRequest request) {
        log.warn("event=invalid_status_transition path={} correlationId={} detail={}",
                request.getRequestURI(), MDC.get("correlationId"), ex.getMessage());

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                "INVALID_STATUS_TRANSITION",
                ex.getMessage(),
                request.getRequestURI(),
                MDC.get("correlationId")
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("event=unhandled_exception path={} correlationId={} exceptionType={}",
                request.getRequestURI(), MDC.get("correlationId"), ex.getClass().getName(), ex);

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                request.getRequestURI(),
                MDC.get("correlationId")
        );
        return ResponseEntity.internalServerError().body(body);
    }
}
