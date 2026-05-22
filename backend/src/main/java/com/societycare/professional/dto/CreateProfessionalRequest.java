package com.societycare.professional.dto;

import com.societycare.complaint.Category;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class CreateProfessionalRequest {

    @NotBlank(message = "name must not be blank")
    @Size(max = 120, message = "name must be at most 120 characters")
    private String name;

    @NotBlank(message = "phone must not be blank")
    @Size(max = 32, message = "phone must be at most 32 characters")
    private String phone;

    @NotNull(message = "category is required")
    private Category category;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }
}
