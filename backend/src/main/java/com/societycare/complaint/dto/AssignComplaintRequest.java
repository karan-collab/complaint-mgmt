package com.societycare.complaint.dto;

import javax.validation.constraints.NotNull;

/**
 * Body for POST /api/v1/complaints/{id}/assign.
 */
public class AssignComplaintRequest {

    @NotNull(message = "professionalId is required")
    private Long professionalId;

    public Long getProfessionalId() {
        return professionalId;
    }

    public void setProfessionalId(Long professionalId) {
        this.professionalId = professionalId;
    }
}
