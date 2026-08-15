package com.societycare.complaint;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    List<Complaint> findAllByOrderByCreatedAtDesc();

    List<Complaint> findByResident_FlatNoIgnoreCaseOrderByCreatedAtDesc(String flatNo);

    boolean existsByProfessional_ProfessionalId(Long professionalId);

    /** Used when an admin deletes a resident: their complaints go with them. */
    void deleteByResident_ResidentId(Long residentId);
}
