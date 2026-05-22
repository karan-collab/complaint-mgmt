package com.societycare.common;

/**
 * Thrown when the request would violate the resource's current state, such as:
 *   - assigning a worker to a complaint that already has one
 *   - completing a complaint that is not yet assigned
 *   - deleting a professional referenced by a complaint
 *
 * Mapped to HTTP 409 Conflict.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
