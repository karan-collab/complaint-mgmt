package com.societycare.resident;

import com.societycare.common.ConflictException;
import com.societycare.common.NotFoundException;
import com.societycare.complaint.ComplaintRepository;
import com.societycare.resident.dto.CreateResidentRequest;
import com.societycare.resident.dto.ResetPasswordRequest;
import com.societycare.resident.dto.ResidentDto;
import com.societycare.resident.dto.UpdateResidentRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ResidentService {

    private final ResidentRepository residentRepository;
    private final ComplaintRepository complaintRepository;
    private final PasswordEncoder passwordEncoder;

    public ResidentService(ResidentRepository residentRepository,
                           ComplaintRepository complaintRepository,
                           PasswordEncoder passwordEncoder) {
        this.residentRepository = residentRepository;
        this.complaintRepository = complaintRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<ResidentDto> findAll() {
        return residentRepository.findAll().stream()
                .map(ResidentDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public ResidentDto create(CreateResidentRequest request) {
        String flatNo = request.getFlatNo().trim();
        residentRepository.findByFlatNoIgnoreCase(flatNo).ifPresent(existing -> {
            throw new ConflictException("Flat " + flatNo + " is already registered");
        });

        Resident resident = new Resident(request.getName().trim(), flatNo);
        resident.setPhone(blankToNull(request.getPhone()));
        resident.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        return ResidentDto.from(residentRepository.save(resident));
    }

    /**
     * Partial update of a resident's details. Null fields are left unchanged;
     * phone is the one field that can be cleared by sending an empty string.
     */
    @Transactional
    public ResidentDto update(Long residentId, UpdateResidentRequest request) {
        Resident resident = getResidentOrThrow(residentId);

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            resident.setResidentName(request.getName().trim());
        }
        if (request.getFlatNo() != null && !request.getFlatNo().trim().isEmpty()) {
            String flatNo = request.getFlatNo().trim();
            residentRepository.findByFlatNoIgnoreCase(flatNo).ifPresent(existing -> {
                if (!existing.getResidentId().equals(residentId)) {
                    throw new ConflictException("Flat " + flatNo + " is already registered");
                }
            });
            resident.setFlatNo(flatNo);
        }
        if (request.getPhone() != null) {
            resident.setPhone(blankToNull(request.getPhone()));
        }

        return ResidentDto.from(residentRepository.save(resident));
    }

    /**
     * Permanently delete a resident along with every complaint they raised.
     * Complaints carry a non-null FK to the resident, so they cannot be kept
     * behind; the admin UI warns with the exact count before calling this.
     */
    @Transactional
    public void delete(Long residentId) {
        Resident resident = getResidentOrThrow(residentId);
        complaintRepository.deleteByResident_ResidentId(residentId);
        residentRepository.delete(resident);
    }

    private Resident getResidentOrThrow(Long residentId) {
        return residentRepository.findById(residentId)
                .orElseThrow(() -> new NotFoundException("Resident not found: " + residentId));
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Admin-driven password reset. Bypasses current-password verification. */
    @Transactional
    public void resetPassword(Long residentId, ResetPasswordRequest request) {
        Resident resident = residentRepository.findById(residentId)
                .orElseThrow(() -> new NotFoundException("Resident not found: " + residentId));
        resident.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        residentRepository.save(resident);
    }
}
