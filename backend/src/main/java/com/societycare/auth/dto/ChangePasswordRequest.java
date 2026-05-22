package com.societycare.auth.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** Body for {@code POST /api/v1/auth/me/password}. Self-service password change. */
public class ChangePasswordRequest {

    @NotBlank(message = "currentPassword is required")
    private String currentPassword;

    @NotBlank(message = "newPassword is required")
    @Size(min = 6, max = 100, message = "newPassword must be 6-100 characters")
    private String newPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
