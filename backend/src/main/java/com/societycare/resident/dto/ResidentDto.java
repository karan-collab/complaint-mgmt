package com.societycare.resident.dto;

import com.societycare.resident.Resident;

import java.time.OffsetDateTime;

/** Read-only resident view exposed to the admin UI. Never includes the password hash. */
public class ResidentDto {

    private final Long id;
    private final String name;
    private final String flatNo;
    private final OffsetDateTime createdAt;

    public ResidentDto(Long id, String name, String flatNo, OffsetDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.flatNo = flatNo;
        this.createdAt = createdAt;
    }

    public static ResidentDto from(Resident r) {
        return new ResidentDto(r.getResidentId(), r.getResidentName(), r.getFlatNo(), r.getCreatedAt());
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getFlatNo() {
        return flatNo;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
