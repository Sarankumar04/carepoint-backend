package com.example.doctorappointment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

public record ApiError(Instant timestamp, int status, String code, String message, String path,
                       @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> fieldErrors) {
    public ApiError(Instant timestamp, int status, String code, String message, String path) {
        this(timestamp, status, code, message, path, Map.of());
    }
}
