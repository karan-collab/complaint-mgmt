package com.societycare.suggestion;

import com.societycare.suggestion.dto.SuggestionDto;
import org.springframework.stereotype.Component;

@Component
public class SuggestionMapper {

    public SuggestionDto toDto(Suggestion s) {
        return new SuggestionDto(
                s.getSuggestionId(),
                s.getResident().getFlatNo(),
                s.getResident().getResidentName(),
                s.getResident().getPhone(),
                s.getSuggestion(),
                s.getCreatedAt()
        );
    }
}
