package com.societycare.suggestion;

import com.societycare.security.AuthenticatedUser;
import com.societycare.suggestion.dto.CreateSuggestionRequest;
import com.societycare.suggestion.dto.SuggestionDto;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * Resident feedback. The two sides are strictly separated: a resident may only
 * write, and management may only read. There is no id parameter on the write
 * path, so a resident cannot file a suggestion in somebody else's name, and no
 * resident-facing read path, so one flat's feedback is never visible to another.
 */
@RestController
@RequestMapping("/api/v1/suggestions")
public class SuggestionController {

    private final SuggestionService suggestionService;

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<SuggestionDto> list() {
        return suggestionService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('RESIDENT')")
    public SuggestionDto create(@Valid @RequestBody CreateSuggestionRequest request,
                                @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return suggestionService.create(principal.getUserId(), request);
    }
}
