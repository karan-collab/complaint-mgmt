package com.societycare.professional.dto;

import javax.validation.constraints.Size;

/**
 * Update payload for a professional. Only name and phone can change; category
 * is intentionally excluded so that historical complaints keep referencing a
 * professional whose category matches their own.
 *
 * Both fields are optional; null means "leave unchanged".
 */
public class UpdateProfessionalRequest {

    @Size(max = 120, message = "name must be at most 120 characters")
    private String name;

    @Size(max = 32, message = "phone must be at most 32 characters")
    private String phone;

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
}
