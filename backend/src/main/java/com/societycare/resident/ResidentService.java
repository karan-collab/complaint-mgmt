package com.societycare.resident;

import com.societycare.common.ConflictException;
import com.societycare.common.NotFoundException;
import com.societycare.resident.dto.CreateResidentRequest;
import com.societycare.resident.dto.ResetPasswordRequest;
import com.societycare.resident.dto.ResidentDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ResidentService {

    private final ResidentRepository residentRepository;
    private final PasswordEncoder passwordEncoder;

    public ResidentService(ResidentRepository residentRepository,
                           PasswordEncoder passwordEncoder) {
        this.residentRepository = residentRepository;
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
        resident.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        return ResidentDto.from(residentRepository.save(resident));
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
