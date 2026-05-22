package com.societycare.professional.dto;

import com.societycare.complaint.Category;

public class ProfessionalDto {

    private final Long id;
    private final String name;
    private final String phone;
    private final Category category;

    public ProfessionalDto(Long id, String name, String phone, Category category) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public Category getCategory() {
        return category;
    }
}
