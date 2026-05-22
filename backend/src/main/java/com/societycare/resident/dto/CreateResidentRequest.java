package com.societycare.resident.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** Body for {@code POST /api/v1/admin/residents}. Admin-only. */
public class CreateResidentRequest {

    @NotBlank(message = "name must not be blank")
    @Size(max = 120, message = "name must be at most 120 characters")
    private String name;

    @NotBlank(message = "flatNo must not be blank")
    @Size(max = 16, message = "flatNo must be at most 16 characters")
    @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "flatNo must contain only letters, digits and hyphens")
    private String flatNo;

    @NotBlank(message = "password must not be blank")
    @Size(min = 6, max = 100, message = "password must be 6-100 characters")
    private String password;

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
