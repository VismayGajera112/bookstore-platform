package com.example.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/** One error shape for the whole platform, so clients parse failures the same way everywhere. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String service,
        Map<String, String> fieldErrors
) {

    public static ErrorResponse of(int status, String error, String message, String path, String service) {
        return new ErrorResponse(Instant.now(), status, error, message, path, service, null);
    }

    public static ErrorResponse of(int status, String error, String message, String path, String service,
                                   Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, path, service, fieldErrors);
    }
}
