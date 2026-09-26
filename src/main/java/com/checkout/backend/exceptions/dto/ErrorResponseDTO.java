package com.checkout.backend.exceptions.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;

// API response for a error
    /*
        timestamp: when was the response generated, server hour.
        status: HTTP numeric code.
        error: reason of error.
        message: error description for show.
        path: URI requested without query string.
        fieldErrors: details regarding detalle error validation per parameter.
    */

public record ErrorResponseDTO(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<FieldErrorDTO> fieldErrors
) {

    // Rejected field by Bean Validation
        /*
             field: request field name.
             message: reason of rejection.
        */

    public record FieldErrorDTO(String field, String message) {
    }

    // Error without detail per field.
    public static ErrorResponseDTO of(HttpStatus status, String message, String path) {
        return of(status, message, path, List.of());
    }

    // Validation case: error, list of rejected fields.
    public static ErrorResponseDTO of(HttpStatus status, String message, String path,
                                      List<FieldErrorDTO> fieldErrors) {
        return new ErrorResponseDTO(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                fieldErrors
        );
    }
}