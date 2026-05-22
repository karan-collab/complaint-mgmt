package com.societycare.resident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResidentRepository extends JpaRepository<Resident, Long> {

    Optional<Resident> findByFlatNoIgnoreCase(String flatNo);
}
