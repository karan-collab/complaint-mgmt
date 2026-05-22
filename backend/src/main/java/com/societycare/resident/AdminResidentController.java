package com.societycare.resident;

import com.societycare.resident.dto.CreateResidentRequest;
import com.societycare.resident.dto.ResetPasswordRequest;
import com.societycare.resident.dto.ResidentDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * Admin-only resident management. Residents register / are registered through
 * this endpoint; no public sign-up exists.
 */
@RestController
@RequestMapping("/api/v1/admin/residents")
@PreAuthorize("hasRole('ADMIN')")
public class AdminResidentController {

    private final ResidentService residentService;

    public AdminResidentController(ResidentService residentService) {
        this.residentService = residentService;
    }

    @GetMapping
    public List<ResidentDto> list() {
        return residentService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResidentDto create(@Valid @RequestBody CreateResidentRequest request) {
        return residentService.create(request);
    }

    /**
     * Force-reset a resident's password without knowing the current one.
     * Useful when a resident has forgotten their password and there is no
     * email-based recovery flow.
     */
    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id,
                              @Valid @RequestBody ResetPasswordRequest request) {
        residentService.resetPassword(id, request);
    }
}
