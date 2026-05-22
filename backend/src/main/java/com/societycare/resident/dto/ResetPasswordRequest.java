package com.societycare.resident.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** Body for {@code POST /api/v1/admin/residents/{id}/password}. Admin-only force reset. */
public class ResetPasswordRequest {

    @NotBlank(message = "newPassword is required")
    @Size(min = 6, max = 100, message = "newPassword must be 6-100 characters")
    private String newPassword;

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
