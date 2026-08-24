package com.societycare.notification;

/**
 * The event a notification records. The stored {@code message} carries the text
 * shown to the user; this enum exists so the UI can style or group by kind, and
 * so reporting can count events without parsing prose.
 */
public enum NotificationType {

    /** A resident raised a new complaint. Goes to management. */
    COMPLAINT_RAISED(RecipientType.ADMIN),

    /** A resident withdrew a complaint. Goes to management. */
    COMPLAINT_WITHDRAWN(RecipientType.ADMIN),

    /**
     * A resident shared a suggestion. Goes to management, and is the one type
     * whose subject is a suggestion rather than a complaint.
     */
    SUGGESTION_RAISED(RecipientType.ADMIN),

    /** A worker was assigned to a complaint with nobody on it. */
    WORKER_ASSIGNED(RecipientType.RESIDENT),

    /** A complaint already in Pending Work was handed to a different worker. */
    WORKER_REASSIGNED(RecipientType.RESIDENT),

    /** The assigned worker was taken off; the ticket is back in the queue. */
    WORKER_REMOVED(RecipientType.RESIDENT),

    /** The work was marked done. */
    COMPLAINT_COMPLETED(RecipientType.RESIDENT),

    /** A completed ticket was reopened; it starts over with no worker. */
    COMPLAINT_REOPENED(RecipientType.RESIDENT);

    private final RecipientType recipientType;

    NotificationType(RecipientType recipientType) {
        this.recipientType = recipientType;
    }

    public RecipientType getRecipientType() {
        return recipientType;
    }
}
