package com.societycare.complaint;

import org.springframework.data.jpa.repository.JpaRepository;

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

    boolean existsByProfessional_ProfessionalId(Long professionalId);

    /** Used when an admin deletes a resident: their complaints go with them. */
    void deleteByResident_ResidentId(Long residentId);
}
