package com.example.incident.dto;

import java.time.Instant;
import java.util.List;

/**
 * Consistent error envelope for every failure case in the API.
 *
 * "code" is a stable, machine-readable string (e.g. VALIDATION_ERROR,
 * INCIDENT_NOT_FOUND) that callers can switch on programmatically without
 * parsing the human-readable "message". "fieldErrors" is populated only for
 * request-validation failures.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(int status, String code, String message, String path, String correlationId) {
        return new ErrorResponse(Instant.now(), status, code, message, path, correlationId, List.of());
    }

    public static ErrorResponse of(int status, String code, String message, String path, String correlationId, List<FieldError> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, code, message, path, correlationId, fieldErrors);
    }
}
