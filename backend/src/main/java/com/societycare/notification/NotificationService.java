package com.societycare.notification;

import com.societycare.complaint.Complaint;
import com.societycare.notification.dto.NotificationDto;
import com.societycare.professional.Professional;
import com.societycare.security.AuthenticatedUser;
import com.societycare.suggestion.Suggestion;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes and reads the in-app notification feed.
 *
 * The {@code record*} methods are called from ComplaintService at each state
 * transition. They build the message text at write time on purpose: the row is
 * a record of what happened, so a later reassignment must not rewrite the
 * wording of an older notification.
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    /** How many rows the bell panel shows. Older ones stay for reporting. */
    private static final int FEED_LIMIT = 50;

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
    }

    // ------------------------------------------------------------- writing

    @Transactional
    public void recordComplaintRaised(Complaint complaint) {
        save(complaint, NotificationType.COMPLAINT_RAISED,
                "Flat " + flatOf(complaint) + " raised a new " + categoryOf(complaint) + " issue");
    }

    @Transactional
    public void recordComplaintWithdrawn(Complaint complaint) {
        save(complaint, NotificationType.COMPLAINT_WITHDRAWN,
                "Flat " + flatOf(complaint) + " withdrew " + articleFor(categoryOf(complaint))
                        + " " + categoryOf(complaint) + " issue");
    }

    /**
     * Management is told when a resident shares a suggestion. The text says who
     * it came from but not what it says: the panel is a nudge to go and read it,
     * and a 255-character message column would truncate most suggestions anyway.
     */
    @Transactional
    public void recordSuggestionRaised(Suggestion suggestion) {
        Notification notification = new Notification();
        notification.setType(NotificationType.SUGGESTION_RAISED);
        notification.setRecipientType(NotificationType.SUGGESTION_RAISED.getRecipientType());
        notification.setSuggestion(suggestion);
        notification.setMessage(truncate(
                "Flat " + suggestion.getResident().getFlatNo() + " shared a new suggestion"));
        notificationRepository.save(notification);
    }

    @Transactional
    public void recordWorkerAssigned(Complaint complaint, Professional worker, boolean reassigned) {
        String who = worker.getName() + " (" + worker.getCategory().getLabel() + ")";
        if (reassigned) {
            save(complaint, NotificationType.WORKER_REASSIGNED,
                    "Your " + categoryOf(complaint) + " complaint has been reassigned to " + who);
        } else {
            save(complaint, NotificationType.WORKER_ASSIGNED,
                    who + " has been assigned to your " + categoryOf(complaint) + " complaint");
        }
    }

    @Transactional
    public void recordWorkerRemoved(Complaint complaint, Professional previousWorker) {
        String who = previousWorker == null ? "The assigned worker" : previousWorker.getName();
        save(complaint, NotificationType.WORKER_REMOVED,
                who + " was removed from your " + categoryOf(complaint)
                        + " complaint. It is back in the assignment queue.");
    }

    @Transactional
    public void recordComplaintCompleted(Complaint complaint) {
        save(complaint, NotificationType.COMPLAINT_COMPLETED,
                "Your " + categoryOf(complaint) + " complaint has been marked complete");
    }

    @Transactional
    public void recordComplaintReopened(Complaint complaint) {
        save(complaint, NotificationType.COMPLAINT_REOPENED,
                "Your " + categoryOf(complaint)
                        + " complaint has been reopened and is back in the assignment queue");
    }

    private void save(Complaint complaint, NotificationType type, String message) {
        Notification notification = new Notification();
        notification.setType(type);
        notification.setRecipientType(type.getRecipientType());
        // Management notifications are addressed to the role, so they carry no
        // resident; the CHECK constraint in V5 enforces that pairing.
        if (type.getRecipientType() == RecipientType.RESIDENT) {
            notification.setResident(complaint.getResident());
        }
        notification.setComplaint(complaint);
        notification.setMessage(truncate(message));
        notificationRepository.save(notification);
    }

    // ------------------------------------------------------------- reading

    public List<NotificationDto> findFor(AuthenticatedUser principal) {
        Pageable limit = PageRequest.of(0, FEED_LIMIT);
        List<Notification> rows = principal.isAdmin()
                ? notificationRepository.findForRecipientType(RecipientType.ADMIN, limit)
                : notificationRepository.findForResident(principal.getUserId(), limit);
        return rows.stream().map(notificationMapper::toDto).collect(Collectors.toList());
    }

    public long unreadCountFor(AuthenticatedUser principal) {
        return principal.isAdmin()
                ? notificationRepository.countByRecipientTypeAndReadAtIsNull(RecipientType.ADMIN)
                : notificationRepository.countByResident_ResidentIdAndReadAtIsNull(principal.getUserId());
    }

    /** Called when the panel is opened; clears the red dot. */
    @Transactional
    public int markAllRead(AuthenticatedUser principal) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return principal.isAdmin()
                ? notificationRepository.markAllReadForRecipientType(RecipientType.ADMIN, now)
                : notificationRepository.markAllReadForResident(principal.getUserId(), now);
    }

    // ---------------------------------------------------------- housekeeping

    /**
     * Removes everything raised before {@code cutoff}, read or not. Called by
     * the retention job; kept public so tests can drive it directly rather than
     * waiting for the schedule.
     */
    @Transactional
    public int purgeOlderThan(OffsetDateTime cutoff) {
        return notificationRepository.deleteOlderThan(cutoff);
    }

    /**
     * Clears every notification connected to a resident who is being deleted -
     * their own, and the management ones about their complaints and their
     * suggestions. Must run before those complaints and suggestions are
     * deleted; see ResidentService.delete.
     */
    @Transactional
    public int purgeForResident(Long residentId) {
        return notificationRepository.deleteAllForResident(residentId);
    }

    // ---------------------------------------------------------------- utils

    private static String flatOf(Complaint complaint) {
        return complaint.getResident().getFlatNo();
    }

    private static String categoryOf(Complaint complaint) {
        return complaint.getCategory().getLabel();
    }

    /**
     * "an Electrician issue", not "a Electrician issue". Only needed where the
     * article sits directly against the category - "raised a new Plumber issue"
     * reads off "new", so it is always "a" there.
     */
    private static String articleFor(String categoryLabel) {
        if (categoryLabel == null || categoryLabel.isEmpty()) return "a";
        return "AEIOU".indexOf(Character.toUpperCase(categoryLabel.charAt(0))) >= 0 ? "an" : "a";
    }

    /** message is VARCHAR(255); worker and flat names are user-supplied. */
    private static String truncate(String message) {
        return message.length() <= 255 ? message : message.substring(0, 252) + "...";
    }
}
