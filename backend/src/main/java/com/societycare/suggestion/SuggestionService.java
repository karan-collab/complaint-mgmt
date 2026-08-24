package com.societycare.suggestion;

import com.societycare.common.NotFoundException;
import com.societycare.notification.NotificationService;
import com.societycare.resident.Resident;
import com.societycare.resident.ResidentRepository;
import com.societycare.suggestion.dto.CreateSuggestionRequest;
import com.societycare.suggestion.dto.SuggestionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SuggestionService {

    private final SuggestionRepository suggestionRepository;
    private final ResidentRepository residentRepository;
    private final NotificationService notificationService;
    private final SuggestionMapper suggestionMapper;

    public SuggestionService(SuggestionRepository suggestionRepository,
                             ResidentRepository residentRepository,
                             NotificationService notificationService,
                             SuggestionMapper suggestionMapper) {
        this.suggestionRepository = suggestionRepository;
        this.residentRepository = residentRepository;
        this.notificationService = notificationService;
        this.suggestionMapper = suggestionMapper;
    }

    /** Every suggestion in the society, newest first. Admin only. */
    public List<SuggestionDto> findAll() {
        return suggestionRepository.findAllNewestFirst().stream()
                .map(suggestionMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public SuggestionDto create(Long residentId, CreateSuggestionRequest request) {
        Resident resident = residentRepository.findById(residentId)
                .orElseThrow(() -> new NotFoundException("Resident not found: " + residentId));
        Suggestion saved = suggestionRepository.save(
                new Suggestion(resident, request.getSuggestion().trim()));
        // After the save, never before: the notification carries a foreign key
        // to the suggestion, so the row has to exist and have an id first.
        notificationService.recordSuggestionRaised(saved);
        return suggestionMapper.toDto(saved);
    }
}
