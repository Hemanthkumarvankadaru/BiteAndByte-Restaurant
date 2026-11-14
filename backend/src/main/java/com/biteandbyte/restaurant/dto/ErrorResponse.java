package com.biteandbyte.restaurant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Standard error body.
 *
 * @param status      HTTP status code
 * @param error       stable machine-readable code, for example {@code VALIDATION_FAILED}
 * @param message     human-readable explanation, safe to show to users
 * @param path        request path that failed
 * @param fieldErrors per-field validation problems, present only for validation errors
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        boolean success,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors,
        Instant timestamp) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(false, status, error, message, path, List.of(), Instant.now());
    }

    public static ErrorResponse withFields(int status, String error, String message, String path,
                                           List<FieldError> fieldErrors) {
        return new ErrorResponse(false, status, error, message, path, List.copyOf(fieldErrors), Instant.now());
    }
}
