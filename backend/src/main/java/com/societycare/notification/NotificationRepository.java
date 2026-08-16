package com.societycare.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * The complaint (and its resident) are fetched eagerly here: the panel shows
     * the flat and category on every row, and a lazy load per row would turn one
     * query into fifty.
     */
    @Query("select n from Notification n "
            + "join fetch n.complaint c join fetch c.resident "
            + "where n.resident.residentId = :residentId "
            + "order by n.createdAt desc")
    List<Notification> findForResident(@Param("residentId") Long residentId, Pageable pageable);

    @Query("select n from Notification n "
            + "join fetch n.complaint c join fetch c.resident "
            + "where n.recipientType = :recipientType "
            + "order by n.createdAt desc")
    List<Notification> findForRecipientType(@Param("recipientType") RecipientType recipientType,
                                            Pageable pageable);

    /** Hot path: runs on every poll of every signed-in user. */
    long countByResident_ResidentIdAndReadAtIsNull(Long residentId);

    long countByRecipientTypeAndReadAtIsNull(RecipientType recipientType);

    // A resident_id is only ever set on RESIDENT rows, so this needs no
    // recipient_type filter of its own.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :now "
            + "where n.readAt is null and n.resident.residentId = :residentId")
    int markAllReadForResident(@Param("residentId") Long residentId, @Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :now "
            + "where n.readAt is null and n.recipientType = :recipientType")
    int markAllReadForRecipientType(@Param("recipientType") RecipientType recipientType,
                                    @Param("now") OffsetDateTime now);

    /**
     * Everything tied to a resident who is being removed: both the notifications
     * addressed to them and the management notifications *about* their
     * complaints, which are about to be hard-deleted along with the resident.
     *
     * Must run before the complaints are deleted, or the complaint_id foreign
     * key rejects the delete - see ResidentService.delete.
     */
    // The complaint side has to go through a subquery: walking
    // n.complaint.resident in a bulk delete makes Hibernate emit an implicit
    // "cross join", which is not valid in a DELETE statement. Reaching
    // n.resident.residentId is fine - that is the local foreign key column, no
    // join needed.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n "
            + "where n.resident.residentId = :residentId "
            + "   or n.complaint in (select c from Complaint c "
            + "                      where c.resident.residentId = :residentId)")
    int deleteAllForResident(@Param("residentId") Long residentId);

    /** Retention sweep; see NotificationRetentionJob. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") OffsetDateTime cutoff);
}
