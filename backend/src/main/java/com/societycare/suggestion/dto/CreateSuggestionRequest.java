package com.societycare.suggestion.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/suggestions}. The resident is taken from the
 * authenticated principal so it cannot be spoofed by a client.
 */
public class CreateSuggestionRequest {

    @NotBlank(message = "suggestion must not be blank")
    @Size(min = 5, max = 2000, message = "suggestion must be between 5 and 2000 characters")
    private String suggestion;

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }
}
