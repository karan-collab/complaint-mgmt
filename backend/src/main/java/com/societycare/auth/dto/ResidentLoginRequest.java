package com.societycare.auth.dto;

import javax.validation.constraints.NotBlank;

/** Body for {@code POST /api/v1/auth/resident/login}. */
public class ResidentLoginRequest {

    @NotBlank(message = "flatNo is required")
    private String flatNo;

    @NotBlank(message = "password is required")
    private String password;

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
