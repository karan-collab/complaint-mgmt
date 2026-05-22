package com.societycare.auth;

import com.societycare.admin.Admin;
import com.societycare.admin.AdminRepository;
import com.societycare.auth.dto.AdminLoginRequest;
import com.societycare.auth.dto.ChangePasswordRequest;
import com.societycare.auth.dto.LoginResponse;
import com.societycare.auth.dto.ResidentLoginRequest;
import com.societycare.common.BadRequestException;
import com.societycare.common.NotFoundException;
import com.societycare.resident.Resident;
import com.societycare.resident.ResidentRepository;
import com.societycare.security.AuthenticatedUser;
import com.societycare.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final ResidentRepository residentRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(ResidentRepository residentRepository,
                       AdminRepository adminRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.residentRepository = residentRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public LoginResponse loginResident(ResidentLoginRequest req) {
        // Always run the encoder, even on a missing flat, to limit timing-based enumeration.
        Resident resident = residentRepository.findByFlatNoIgnoreCase(req.getFlatNo().trim()).orElse(null);
        String hash = resident == null ? "$2a$10$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvali" : resident.getPasswordHash();
        if (hash == null || !passwordEncoder.matches(req.getPassword(), hash) || resident == null) {
            throw new BadCredentialsException("Invalid flat number or password");
        }
        String token = jwtService.issueForResident(
                resident.getResidentId(), resident.getResidentName(), resident.getFlatNo());
        return LoginResponse.forResident(token, jwtService.ttlSeconds(),
                resident.getResidentId(), resident.getResidentName(), resident.getFlatNo());
    }

    @Transactional(readOnly = true)
    public LoginResponse loginAdmin(AdminLoginRequest req) {
        Admin admin = adminRepository.findByUsername(req.getUsername().trim()).orElse(null);
        String hash = admin == null ? "$2a$10$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvali" : admin.getPasswordHash();
        if (hash == null || !passwordEncoder.matches(req.getPassword(), hash) || admin == null) {
            throw new BadCredentialsException("Invalid username or password");
        }
        String token = jwtService.issueForAdmin(admin.getAdminId(), admin.getUsername());
        return LoginResponse.forAdmin(token, jwtService.ttlSeconds(),
                admin.getAdminId(), admin.getUsername());
    }

    /**
     * Lets the authenticated caller (resident or admin) change their own password
     * after re-supplying the current one. Existing JWTs remain valid until they
     * expire — the user can re-login if they want to invalidate them sooner.
     *
     * <p>A wrong "current password" is reported as {@code 400 Bad Request}, not
     * {@code 401 Unauthorized}: the JWT is fully valid, only the form input is
     * incorrect. Returning 401 would cause clients to assume the session expired
     * and force a logout, which is a poor UX for a self-service password change.
     */
    @Transactional
    public void changeMyPassword(AuthenticatedUser principal, ChangePasswordRequest req) {
        if (req.getCurrentPassword().equals(req.getNewPassword())) {
            throw new BadRequestException("New password must differ from current password");
        }

        if (principal.isAdmin()) {
            Admin admin = adminRepository.findById(principal.getUserId())
                    .orElseThrow(() -> new NotFoundException("Admin not found: " + principal.getUserId()));
            if (!passwordEncoder.matches(req.getCurrentPassword(), admin.getPasswordHash())) {
                throw new BadRequestException("Current password is incorrect");
            }
            admin.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
            adminRepository.save(admin);
            return;
        }

        Resident resident = residentRepository.findById(principal.getUserId())
                .orElseThrow(() -> new NotFoundException("Resident not found: " + principal.getUserId()));
        String hash = resident.getPasswordHash();
        if (hash == null || !passwordEncoder.matches(req.getCurrentPassword(), hash)) {
            throw new BadRequestException("Current password is incorrect");
        }
        resident.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        residentRepository.save(resident);
    }
}
