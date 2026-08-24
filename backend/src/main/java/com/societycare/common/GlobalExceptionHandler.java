package com.societycare.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralizes error responses across every controller. Every handler returns
 * an {@link ApiError} payload so callers always see the same JSON shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage(), request);
    }

    /**
     * A query or path parameter that cannot be coerced to its declared type -
     * {@code ?from=31-03-2026} against a LocalDate, or a non-numeric id.
     *
     * Without this the mismatch escapes as a 500, which is wrong twice over: it
     * blames the server for the caller's typo, and it hands back an opaque
     * error instead of naming the parameter at fault.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       WebRequest request) {
        String detail = "'" + ex.getName() + "' has an invalid value";
        if (ex.getValue() != null) {
            detail += ": " + ex.getValue();
        }
        return build(HttpStatus.BAD_REQUEST, "Bad request", detail, request);
    }

    /** Triggered by @Valid on @RequestBody DTOs. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(),
                        fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .collect(Collectors.toList());

        ApiError body = new ApiError(
                "Validation failed",
                HttpStatus.BAD_REQUEST.value(),
                "Request body has invalid fields",
                instance(request),
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Triggered by @Validated on path/query params (e.g. enum coercion). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<ApiError.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .collect(Collectors.toList());

        ApiError body = new ApiError(
                "Validation failed",
                HttpStatus.BAD_REQUEST.value(),
                "Request parameters are invalid",
                instance(request),
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /** Triggered when JSON cannot be parsed or an enum value is unknown. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request",
                "Request body could not be parsed", request);
    }

    /**
     * Triggered when @PreAuthorize denies access. Anonymous callers get 401
     * (they're not authenticated yet); authenticated-but-unauthorized callers get 403.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return build(HttpStatus.UNAUTHORIZED, "Unauthenticated",
                    "Authentication is required to access this resource", request);
        }
        return build(HttpStatus.FORBIDDEN, "Access denied",
                "You do not have permission to perform this action", request);
    }

    /**
     * Catches authentication failures that surface inside MVC (e.g. credentials
     * thrown from a service). Filter-level 401s never reach here; they are handled
     * by the {@code AuthenticationEntryPoint} configured in SecurityConfig.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, WebRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthenticated",
                ex.getMessage() == null ? "Authentication failed" : ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAnythingElse(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "Unexpected error", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String title, String detail, WebRequest request) {
        ApiError body = new ApiError(title, status.value(), detail, instance(request));
        return ResponseEntity.status(status).body(body);
    }

    private String instance(WebRequest request) {
        String desc = request.getDescription(false);
        return desc != null && desc.startsWith("uri=") ? desc.substring(4) : desc;
    }
}
