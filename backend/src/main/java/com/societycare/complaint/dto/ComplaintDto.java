package com.societycare.complaint.dto;

import com.societycare.complaint.Category;

import java.time.OffsetDateTime;

public class ComplaintDto {

    private final Long id;
    private final String flatNo;
    private final String residentName;
    private final String residentPhone;
    private final Category category;
    private final String description;
    private final StatusDto status;
    private final ProfessionalSummaryDto professional;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime assignedAt;
    private final OffsetDateTime completedAt;

    public ComplaintDto(Long id,
                        String flatNo,
                        String residentName,
                        String residentPhone,
                        Category category,
                        String description,
                        StatusDto status,
                        ProfessionalSummaryDto professional,
                        OffsetDateTime createdAt,
                        OffsetDateTime assignedAt,
                        OffsetDateTime completedAt) {
        this.id = id;
        this.flatNo = flatNo;
        this.residentName = residentName;
        this.residentPhone = residentPhone;
        this.category = category;
        this.description = description;
        this.status = status;
        this.professional = professional;
        this.createdAt = createdAt;
        this.assignedAt = assignedAt;
        this.completedAt = completedAt;
    }

    public Long getId() {
        return id;
    }

    public String getFlatNo() {
        return flatNo;
    }

    public String getResidentName() {
        return residentName;
    }

    /** Contact number of the flat owner, when one is on record. */
    public String getResidentPhone() {
        return residentPhone;
    }

    public Category getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public StatusDto getStatus() {
        return status;
    }

    public ProfessionalSummaryDto getProfessional() {
        return professional;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }
}
