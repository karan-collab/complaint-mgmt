package com.societycare.professional;

import com.societycare.complaint.Category;
import com.societycare.professional.dto.CreateProfessionalRequest;
import com.societycare.professional.dto.ProfessionalDto;
import com.societycare.professional.dto.UpdateProfessionalRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/professionals")
public class ProfessionalController {

    private final ProfessionalService professionalService;

    public ProfessionalController(ProfessionalService professionalService) {
        this.professionalService = professionalService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ProfessionalDto> list(@RequestParam(name = "category", required = false) Category category) {
        return professionalService.findAll(category);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProfessionalDto getById(@PathVariable Long id) {
        return professionalService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProfessionalDto create(@Valid @RequestBody CreateProfessionalRequest request) {
        return professionalService.create(request);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProfessionalDto update(@PathVariable Long id,
                                  @Valid @RequestBody UpdateProfessionalRequest request) {
        return professionalService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        professionalService.delete(id);
    }
}
