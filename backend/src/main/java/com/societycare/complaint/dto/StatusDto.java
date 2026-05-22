package com.societycare.complaint.dto;

public class StatusDto {

    private final Integer id;
    private final String name;

    public StatusDto(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
