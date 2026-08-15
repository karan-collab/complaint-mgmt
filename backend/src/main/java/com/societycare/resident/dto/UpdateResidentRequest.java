package com.societycare.resident.dto;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * Body for {@code PATCH /api/v1/admin/residents/{id}}. Admin-only.
 *
 * Every field is optional; null means "leave unchanged". The password is not
 * editable here — use {@code POST /{id}/password} for that.
 */
public class UpdateResidentRequest {

    @Size(max = 120, message = "name must be at most 120 characters")
    private String name;

    @Size(max = 16, message = "flatNo must be at most 16 characters")
    @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "flatNo must contain only letters, digits and hyphens")
    private String flatNo;

    @Size(max = 32, message = "phone must be at most 32 characters")
    private String phone;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFlatNo() {
        return flatNo;
    }

    public void setFlatNo(String flatNo) {
        this.flatNo = flatNo;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
