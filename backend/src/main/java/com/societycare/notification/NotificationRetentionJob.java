package com.societycare.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Notifications are transient by design: they tell you something changed, and
 * after a while nobody is going to act on them. Anything older than the
 * retention window is deleted, read or not.
 *
 * The complaints themselves are untouched by this - they are the society's
 * record of work done and are never auto-deleted.
 */
@Component
public class NotificationRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetentionJob.class);

    private final NotificationService notificationService;
    private final int retentionDays;

    public NotificationRetentionJob(NotificationService notificationService,
                                    @Value("${societycare.notifications.retention-days:45}") int retentionDays) {
        this.notificationService = notificationService;
        this.retentionDays = retentionDays;
    }

    /** 03:30 daily - quiet hours, and well clear of any backup window. */
    @Scheduled(cron = "0 30 3 * * *")
    public void purgeExpired() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays);
        int removed = notificationService.purgeOlderThan(cutoff);
        if (removed > 0) {
            log.info("Purged {} notification(s) older than {} days", removed, retentionDays);
        }
    }
}
