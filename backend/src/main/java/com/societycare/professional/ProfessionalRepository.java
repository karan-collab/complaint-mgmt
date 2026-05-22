package com.societycare.professional;

import com.societycare.complaint.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfessionalRepository extends JpaRepository<Professional, Long> {

    List<Professional> findByCategoryOrderByNameAsc(Category category);

    List<Professional> findAllByOrderByCategoryAscNameAsc();
}
