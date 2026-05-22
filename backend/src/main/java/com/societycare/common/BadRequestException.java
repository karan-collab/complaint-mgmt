package com.societycare.common;

/**
 * Thrown when a request is well-formed JSON but violates a business rule
 * that should be reported as 400 (e.g. category mismatch on assign).
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
