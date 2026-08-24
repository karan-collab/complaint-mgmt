package com.societycare.suggestion.dto;

import java.time.OffsetDateTime;

public class SuggestionDto {

    private final Long id;
    private final String flatNo;
    private final String residentName;
    private final String residentPhone;
    private final String suggestion;
    private final OffsetDateTime createdAt;

    public SuggestionDto(Long id, String flatNo, String residentName, String residentPhone,
                         String suggestion, OffsetDateTime createdAt) {
        this.id = id;
        this.flatNo = flatNo;
        this.residentName = residentName;
        this.residentPhone = residentPhone;
        this.suggestion = suggestion;
        this.createdAt = createdAt;
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

    public String getResidentPhone() {
        return residentPhone;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
