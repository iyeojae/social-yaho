package org.huss.socialsaas.global.response;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        boolean success,
        String code,
        String message,
        Instant timestamp,
        List<FieldValidationError> errors
) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(false, code, message, Instant.now(), List.of());
    }

    public static ErrorResponse of(String code, String message, List<FieldValidationError> errors) {
        return new ErrorResponse(false, code, message, Instant.now(), errors);
    }

    public record FieldValidationError(String field, String reason) {
    }
}

