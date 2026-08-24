package com.societycare.complaint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    List<Complaint> findAllByOrderByCreatedAtDesc();

    List<Complaint> findByResident_FlatNoIgnoreCaseOrderByCreatedAtDesc(String flatNo);

    /**
     * Everything except withdrawn complaints. Soft-deleted rows stay in the
     * table for the record but must not appear in any normal listing.
     */
    List<Complaint> findByStatus_StatusIdNotOrderByCreatedAtDesc(Integer statusId);

    List<Complaint> findByResident_FlatNoIgnoreCaseAndStatus_StatusIdNotOrderByCreatedAtDesc(
            String flatNo, Integer statusId);

    /** Withdrawn complaints only, for reporting on what residents pull and why. */
    List<Complaint> findByStatus_StatusIdOrderByCreatedAtDesc(Integer statusId);

    /**
     * Everything raised in a half-open window, for the management report, minus
     * one status - the caller passes the withdrawn status so those rows are
     * excluded in SQL rather than being fetched and then dropped.
     *
     * The associations are fetched because the report reads the flat, the
     * resident's name, the status and the worker on every row - lazily they
     * would be four extra queries per complaint.
     */
    @Query("select c from Complaint c "
            + "join fetch c.resident "
            + "join fetch c.status "
            + "left join fetch c.professional "
            + "where c.createdAt >= :from and c.createdAt < :toExclusive "
            + "  and c.status.statusId <> :excludedStatusId "
            + "order by c.createdAt asc, c.complaintId asc")
    List<Complaint> findRaisedBetween(@Param("from") OffsetDateTime from,
                                      @Param("toExclusive") OffsetDateTime toExclusive,
                                      @Param("excludedStatusId") Integer excludedStatusId);

    boolean existsByProfessional_ProfessionalId(Long professionalId);

    /** Used when an admin deletes a resident: their complaints go with them. */
    void deleteByResident_ResidentId(Long residentId);
}
