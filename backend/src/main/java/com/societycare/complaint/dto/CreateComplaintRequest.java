package com.societycare.complaint.dto;

import com.societycare.complaint.Category;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/complaints}. The resident is taken from the
 * authenticated principal so it cannot be spoofed by a client.
 */
public class CreateComplaintRequest {

    @NotNull(message = "category is required")
    private Category category;

    @NotBlank(message = "description must not be blank")
    @Size(min = 5, max = 2000, message = "description must be between 5 and 2000 characters")
    private String description;

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
