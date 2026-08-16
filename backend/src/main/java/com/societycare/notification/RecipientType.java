package com.societycare.notification;

/**
 * Who a notification is addressed to. ADMIN means management as a whole, not a
 * particular admin account - see V5__notification.sql for why.
 */
public enum RecipientType {
    RESIDENT,
    ADMIN
}
