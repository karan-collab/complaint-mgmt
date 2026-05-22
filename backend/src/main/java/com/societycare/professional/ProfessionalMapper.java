package com.societycare.professional;

import com.societycare.professional.dto.ProfessionalDto;
import org.springframework.stereotype.Component;

@Component
public class ProfessionalMapper {

    public ProfessionalDto toDto(Professional p) {
        return new ProfessionalDto(p.getProfessionalId(), p.getName(), p.getPhone(), p.getCategory());
    }
}
