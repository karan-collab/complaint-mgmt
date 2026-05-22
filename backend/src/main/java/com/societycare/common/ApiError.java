package com.societycare.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * RFC 7807-style problem details payload returned for every error response.
 * Field-level details (e.g. validation errors) are included in {@link #getErrors()}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ApiError {

    private final String title;
    private final int status;
    private final String detail;
    private final String instance;
    private final OffsetDateTime timestamp;
    private final List<FieldError> errors;

    public ApiError(String title, int status, String detail, String instance, List<FieldError> errors) {
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
        this.timestamp = OffsetDateTime.now(ZoneOffset.UTC);
        this.errors = errors == null ? new ArrayList<>() : errors;
    }

    public ApiError(String title, int status, String detail, String instance) {
        this(title, status, detail, instance, null);
    }

    public String getTitle() {
        return title;
    }

    public int getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public String getInstance() {
        return instance;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public List<FieldError> getErrors() {
        return errors;
    }

    public static class FieldError {

        private final String field;
        private final String message;

        public FieldError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }
    }
}
