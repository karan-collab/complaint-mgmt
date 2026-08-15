package com.societycare.resident.dto;

import com.societycare.resident.Resident;

import java.time.OffsetDateTime;

/** Read-only resident view exposed to the admin UI. Never includes the password hash. */
public class ResidentDto {

    private final Long id;
    private final String name;
    private final String flatNo;
    private final String phone;
    private final OffsetDateTime createdAt;

    public ResidentDto(Long id, String name, String flatNo, String phone, OffsetDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.flatNo = flatNo;
        this.phone = phone;
        this.createdAt = createdAt;
    }

    public static ResidentDto from(Resident r) {
        return new ResidentDto(r.getResidentId(), r.getResidentName(), r.getFlatNo(),
                r.getPhone(), r.getCreatedAt());
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

    public String getPhone() {
        return phone;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
