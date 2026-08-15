package com.societycare.complaint;

import com.societycare.complaint.dto.AssignComplaintRequest;
import com.societycare.complaint.dto.ComplaintDto;
import com.societycare.complaint.dto.CreateComplaintRequest;
import com.societycare.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/complaints")
public class ComplaintController {

    private final ComplaintService complaintService;

    public ComplaintController(ComplaintService complaintService) {
        this.complaintService = complaintService;
    }

    /**
     * Admin can omit {@code flat} to see every complaint or pass any flat to filter.
     * Residents must pass their own flat (any other value yields 403).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RESIDENT')")
    public List<ComplaintDto> list(@RequestParam(name = "flat", required = false) String flat,
                                   @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal.isResident()) {
            String mine = principal.getFlatNo();
            if (flat == null || flat.isBlank()) {
                return complaintService.findByFlat(mine);
            }
            if (!flat.trim().equalsIgnoreCase(mine)) {
                throw new AccessDeniedException("Residents may only view their own flat");
            }
            return complaintService.findByFlat(flat.trim());
        }
        if (flat == null || flat.isBlank()) {
            return complaintService.findAll();
        }
        return complaintService.findByFlat(flat.trim());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RESIDENT')")
    public ComplaintDto getById(@PathVariable Long id,
                                @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal.isResident()) {
            String complaintFlat = complaintService.getFlatNoFor(id);
            if (!complaintFlat.equalsIgnoreCase(principal.getFlatNo())) {
                throw new AccessDeniedException("Residents may only view their own complaints");
            }
        }
        return complaintService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('RESIDENT')")
    public ComplaintDto create(@Valid @RequestBody CreateComplaintRequest request,
                               @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return complaintService.create(principal.getUserId(), request);
    }

    /**
     * Withdraws a complaint. Residents may only delete their own, and only
     * while it is not complete.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'RESIDENT')")
    public void delete(@PathVariable Long id,
                       @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal.isResident()) {
            String complaintFlat = complaintService.getFlatNoFor(id);
            if (!complaintFlat.equalsIgnoreCase(principal.getFlatNo())) {
                throw new AccessDeniedException("Residents may only delete their own complaints");
            }
        }
        complaintService.delete(id);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ComplaintDto assign(@PathVariable Long id,
                               @Valid @RequestBody AssignComplaintRequest request) {
        return complaintService.assign(id, request);
    }

    @PostMapping("/{id}/unassign")
    @PreAuthorize("hasRole('ADMIN')")
    public ComplaintDto unassign(@PathVariable Long id) {
        return complaintService.unassign(id);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ComplaintDto complete(@PathVariable Long id) {
        return complaintService.complete(id);
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasRole('ADMIN')")
    public ComplaintDto reopen(@PathVariable Long id) {
        return complaintService.reopen(id);
    }
}
