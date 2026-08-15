package com.societycare.complaint;

import com.societycare.common.BadRequestException;
import com.societycare.common.ConflictException;
import com.societycare.common.NotFoundException;
import com.societycare.complaint.dto.AssignComplaintRequest;
import com.societycare.complaint.dto.ComplaintDto;
import com.societycare.complaint.dto.CreateComplaintRequest;
import com.societycare.professional.Professional;
import com.societycare.professional.ProfessionalRepository;
import com.societycare.resident.Resident;
import com.societycare.resident.ResidentRepository;
import com.societycare.status.Status;
import com.societycare.status.StatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ComplaintService {

    private final ComplaintRepository complaintRepository;
    private final ResidentRepository residentRepository;
    private final ProfessionalRepository professionalRepository;
    private final StatusRepository statusRepository;
    private final ComplaintMapper complaintMapper;

    public ComplaintService(ComplaintRepository complaintRepository,
                            ResidentRepository residentRepository,
                            ProfessionalRepository professionalRepository,
                            StatusRepository statusRepository,
                            ComplaintMapper complaintMapper) {
        this.complaintRepository = complaintRepository;
        this.residentRepository = residentRepository;
        this.professionalRepository = professionalRepository;
        this.statusRepository = statusRepository;
        this.complaintMapper = complaintMapper;
    }

    public List<ComplaintDto> findAll() {
        return complaintRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(complaintMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<ComplaintDto> findByFlat(String flatNo) {
        return complaintRepository.findByResident_FlatNoIgnoreCaseOrderByCreatedAtDesc(flatNo)
                .stream()
                .map(complaintMapper::toDto)
                .collect(Collectors.toList());
    }

    public ComplaintDto findById(Long id) {
        return complaintMapper.toDto(getComplaintOrThrow(id));
    }

    @Transactional
    public ComplaintDto create(Long residentId, CreateComplaintRequest request) {
        Resident resident = residentRepository.findById(residentId)
                .orElseThrow(() -> new NotFoundException("Resident not found: " + residentId));
        Status assignmentPending = statusRepository.getReferenceById(Status.ASSIGNMENT_PENDING);

        Complaint complaint = new Complaint();
        complaint.setResident(resident);
        complaint.setCategory(request.getCategory());
        complaint.setDescription(request.getDescription().trim());
        complaint.setStatus(assignmentPending);

        Complaint saved = complaintRepository.save(complaint);
        return complaintMapper.toDto(saved);
    }

    /**
     * Loads a complaint and returns its flat number; used by controllers to
     * gate access against a resident principal without leaking the entity.
     */
    public String getFlatNoFor(Long complaintId) {
        return getComplaintOrThrow(complaintId).getResident().getFlatNo();
    }

    /**
     * Assigns a professional, or re-assigns one that is already set. A ticket
     * that is already in 'Pending Work' may be handed to a different worker;
     * a completed ticket may not be touched.
     */
    @Transactional
    public ComplaintDto assign(Long complaintId, AssignComplaintRequest request) {
        Complaint complaint = getComplaintOrThrow(complaintId);
        Professional professional = professionalRepository.findById(request.getProfessionalId())
                .orElseThrow(() -> new NotFoundException("Professional not found: " + request.getProfessionalId()));

        int statusId = complaint.getStatus().getStatusId();
        if (statusId != Status.ASSIGNMENT_PENDING && statusId != Status.PENDING_WORK) {
            throw new ConflictException("Complaint " + complaintId
                    + " cannot be assigned while it is '"
                    + complaint.getStatus().getStatusName() + "'");
        }
        if (complaint.getCategory() != professional.getCategory()) {
            throw new BadRequestException("Professional category " + professional.getCategory()
                    + " does not match complaint category " + complaint.getCategory());
        }

        complaint.setProfessional(professional);
        complaint.setStatus(statusRepository.getReferenceById(Status.PENDING_WORK));
        // Re-assigning restarts the clock: assignedAt tracks the current worker.
        complaint.setAssignedAt(OffsetDateTime.now(ZoneOffset.UTC));

        return complaintMapper.toDto(complaintRepository.save(complaint));
    }

    /**
     * Drops the assigned worker and sends the ticket back to the
     * 'Assignment Pending' queue. Only valid while work is pending.
     */
    @Transactional
    public ComplaintDto unassign(Long complaintId) {
        Complaint complaint = getComplaintOrThrow(complaintId);

        if (complaint.getStatus().getStatusId() != Status.PENDING_WORK) {
            throw new ConflictException("Complaint " + complaintId
                    + " has no assigned worker to remove (current: "
                    + complaint.getStatus().getStatusName() + ")");
        }

        complaint.setProfessional(null);
        complaint.setAssignedAt(null);
        complaint.setStatus(statusRepository.getReferenceById(Status.ASSIGNMENT_PENDING));

        return complaintMapper.toDto(complaintRepository.save(complaint));
    }

    /**
     * Sends a completed ticket back to the 'Assignment Pending' queue, e.g.
     * when the resident reports the work did not actually fix the problem.
     *
     * The schema only allows a professional on a ticket that is assigned or
     * complete, so reopening necessarily clears the previous worker and the
     * assignment / completion timestamps: the ticket starts over.
     */
    @Transactional
    public ComplaintDto reopen(Long complaintId) {
        Complaint complaint = getComplaintOrThrow(complaintId);

        if (complaint.getStatus().getStatusId() != Status.COMPLETE) {
            throw new ConflictException("Complaint " + complaintId
                    + " is not complete, so it cannot be reopened (current: "
                    + complaint.getStatus().getStatusName() + ")");
        }

        complaint.setProfessional(null);
        complaint.setAssignedAt(null);
        complaint.setCompletedAt(null);
        complaint.setStatus(statusRepository.getReferenceById(Status.ASSIGNMENT_PENDING));

        return complaintMapper.toDto(complaintRepository.save(complaint));
    }

    @Transactional
    public ComplaintDto complete(Long complaintId) {
        Complaint complaint = getComplaintOrThrow(complaintId);

        if (complaint.getStatus().getStatusId() != Status.PENDING_WORK) {
            throw new ConflictException("Complaint " + complaintId
                    + " is not in 'Pending Work' state (current: "
                    + complaint.getStatus().getStatusName() + ")");
        }

        complaint.setStatus(statusRepository.getReferenceById(Status.COMPLETE));
        complaint.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));

        return complaintMapper.toDto(complaintRepository.save(complaint));
    }

    /**
     * Deletes a complaint outright, for a resident withdrawing a request that
     * no longer needs doing. Completed complaints are the society's record of
     * work carried out, so those cannot be removed this way.
     */
    @Transactional
    public void delete(Long complaintId) {
        Complaint complaint = getComplaintOrThrow(complaintId);

        if (complaint.getStatus().getStatusId() == Status.COMPLETE) {
            throw new ConflictException("Complaint " + complaintId
                    + " is complete and forms part of the maintenance record, so it cannot be deleted");
        }

        complaintRepository.delete(complaint);
    }

    private Complaint getComplaintOrThrow(Long id) {
        return complaintRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Complaint not found: " + id));
    }
}
