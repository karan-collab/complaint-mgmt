package com.societycare.professional;

import com.societycare.common.ConflictException;
import com.societycare.common.NotFoundException;
import com.societycare.complaint.Category;
import com.societycare.complaint.ComplaintRepository;
import com.societycare.professional.dto.CreateProfessionalRequest;
import com.societycare.professional.dto.ProfessionalDto;
import com.societycare.professional.dto.UpdateProfessionalRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProfessionalService {

    private final ProfessionalRepository professionalRepository;
    private final ComplaintRepository complaintRepository;
    private final ProfessionalMapper professionalMapper;

    public ProfessionalService(ProfessionalRepository professionalRepository,
                               ComplaintRepository complaintRepository,
                               ProfessionalMapper professionalMapper) {
        this.professionalRepository = professionalRepository;
        this.complaintRepository = complaintRepository;
        this.professionalMapper = professionalMapper;
    }

    public List<ProfessionalDto> findAll(Category category) {
        List<Professional> rows = (category == null)
                ? professionalRepository.findAllByOrderByCategoryAscNameAsc()
                : professionalRepository.findByCategoryOrderByNameAsc(category);
        return rows.stream().map(professionalMapper::toDto).collect(Collectors.toList());
    }

    public ProfessionalDto findById(Long id) {
        return professionalMapper.toDto(getProfessionalOrThrow(id));
    }

    @Transactional
    public ProfessionalDto create(CreateProfessionalRequest request) {
        Professional p = new Professional(
                request.getName().trim(),
                request.getPhone().trim(),
                request.getCategory()
        );
        return professionalMapper.toDto(professionalRepository.save(p));
    }

    @Transactional
    public ProfessionalDto update(Long id, UpdateProfessionalRequest request) {
        Professional p = getProfessionalOrThrow(id);
        if (request.getName() != null && !request.getName().isBlank()) {
            p.setName(request.getName().trim());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            p.setPhone(request.getPhone().trim());
        }
        return professionalMapper.toDto(professionalRepository.save(p));
    }

    @Transactional
    public void delete(Long id) {
        Professional p = getProfessionalOrThrow(id);
        if (complaintRepository.existsByProfessional_ProfessionalId(p.getProfessionalId())) {
            throw new ConflictException("Professional " + id
                    + " cannot be deleted because they are referenced by one or more complaints");
        }
        professionalRepository.delete(p);
    }

    private Professional getProfessionalOrThrow(Long id) {
        return professionalRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Professional not found: " + id));
    }
}
