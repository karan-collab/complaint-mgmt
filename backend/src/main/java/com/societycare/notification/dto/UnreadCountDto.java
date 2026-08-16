package com.societycare.notification.dto;

/** Response of GET /notifications/unread-count - deliberately tiny; it is polled. */
public class UnreadCountDto {

    private final long count;

    public UnreadCountDto(long count) {
        this.count = count;
    }

    public long getCount() {
        return count;
    }
}
