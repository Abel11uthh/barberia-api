package com.barberia.api.shared.error;

import java.time.Instant;
import org.springframework.http.HttpStatus;

public record ApiError(Instant timestamp, int status, String code, String message) {
    public static ApiError of(HttpStatus status, String code, String message) {
        return new ApiError(Instant.now(), status.value(), code, message);
    }
}
