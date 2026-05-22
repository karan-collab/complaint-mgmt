package com.societycare.complaint;

import com.societycare.complaint.dto.ComplaintDto;
import com.societycare.complaint.dto.ProfessionalSummaryDto;
import com.societycare.complaint.dto.StatusDto;
import com.societycare.professional.Professional;
import org.springframework.stereotype.Component;

@Component
public class ComplaintMapper {

    public ComplaintDto toDto(Complaint c) {
        return new ComplaintDto(
                c.getComplaintId(),
                c.getResident().getFlatNo(),
                c.getResident().getResidentName(),
                c.getCategory(),
                c.getDescription(),
                new StatusDto(c.getStatus().getStatusId(), c.getStatus().getStatusName()),
                toProfessionalDto(c.getProfessional()),
                c.getCreatedAt(),
                c.getAssignedAt(),
                c.getCompletedAt()
        );
    }

    private ProfessionalSummaryDto toProfessionalDto(Professional p) {
        if (p == null) {
            return null;
        }
        return new ProfessionalSummaryDto(
                p.getProfessionalId(),
                p.getName(),
                p.getPhone(),
                p.getCategory()
        );
    }
}
