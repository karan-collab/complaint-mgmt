package com.societycare.complaint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.societycare.complaint.dto.AssignComplaintRequest;
import com.societycare.complaint.dto.CreateComplaintRequest;
import com.societycare.notification.NotificationRepository;
import com.societycare.professional.Professional;
import com.societycare.professional.ProfessionalRepository;
import com.societycare.resident.Resident;
import com.societycare.resident.ResidentRepository;
import com.societycare.status.StatusRepository;
import com.societycare.suggestion.SuggestionRepository;
import com.societycare.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ComplaintWriteControllerTest {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ResidentRepository residentRepository;
    @Autowired private SuggestionRepository suggestionRepository;
    @Autowired private ProfessionalRepository professionalRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private StatusRepository statusRepository;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long residentId;
    private String residentFlat;
    private Long plumberId;
    private Long electricianId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        notificationRepository.deleteAll();
        complaintRepository.deleteAll();
        professionalRepository.deleteAll();
        suggestionRepository.deleteAll();
        residentRepository.deleteAll();

        Resident anita = residentRepository.save(new Resident("Anita Sharma", "A-101"));
        Professional plumber = professionalRepository.save(
                new Professional("Suresh Patel", "+91 90000 11223", Category.PLUMBER));
        Professional electrician = professionalRepository.save(
                new Professional("Anil Yadav", "+91 99887 76655", Category.ELECTRICIAN));

        residentId = anita.getResidentId();
        residentFlat = anita.getFlatNo();
        plumberId = plumber.getProfessionalId();
        electricianId = electrician.getProfessionalId();
    }

    private CreateComplaintRequest createReq(Category category, String description) {
        CreateComplaintRequest r = new CreateComplaintRequest();
        r.setCategory(category);
        r.setDescription(description);
        return r;
    }

    private AssignComplaintRequest assignReq(Long professionalId) {
        AssignComplaintRequest r = new AssignComplaintRequest();
        r.setProfessionalId(professionalId);
        return r;
    }

    private Long createComplaint(Category category, String description) throws Exception {
        String json = mockMvc.perform(post("/api/v1/complaints")
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(createReq(category, description))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    @Test
    void create_happy_setsStatusAssignmentPending() throws Exception {
        mockMvc.perform(post("/api/v1/complaints")
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(createReq(Category.PLUMBER, "Leaking sink in kitchen"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status.name").value("Assignment Pending"))
                .andExpect(jsonPath("$.professional").doesNotExist())
                .andExpect(jsonPath("$.flatNo").value("A-101"))
                .andExpect(jsonPath("$.assignedAt").doesNotExist())
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void create_blankDescription_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/complaints")
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(createReq(Category.PLUMBER, ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[?(@.field=='description')]").exists());
    }

    @Test
    void create_unknownResident_returns404WhenJwtPointsAtMissingUser() throws Exception {
        // Resident principal carries an id that doesn't exist in the database.
        mockMvc.perform(post("/api/v1/complaints")
                .with(TestAuth.asResident(999_999L, "Ghost", "X-999"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(createReq(Category.PLUMBER, "Leaking sink in kitchen"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void create_unknownCategory_returns400() throws Exception {
        String body = "{\"category\":\"NOT_A_CATEGORY\",\"description\":\"hello world\"}";
        mockMvc.perform(post("/api/v1/complaints")
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_byAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/complaints")
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(createReq(Category.PLUMBER, "Leaking sink in kitchen"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void assign_happy_movesToPendingWork() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");

        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.name").value("Pending Work"))
                .andExpect(jsonPath("$.professional.id").value(plumberId))
                .andExpect(jsonPath("$.assignedAt").exists())
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void assign_byResident_returns403() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");

        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isForbidden());
    }

    @Test
    void assign_categoryMismatch_returns400() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");

        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(electricianId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("does not match")));
    }

    @Test
    void assign_alreadyAssigned_reassignsToTheNewWorker() throws Exception {
        Long otherPlumberId = professionalRepository.save(
                new Professional("Deepak Rao", "+91 90000 44556", Category.PLUMBER)).getProfessionalId();

        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");
        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(otherPlumberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professional.id").value(otherPlumberId))
                .andExpect(jsonPath("$.professional.name").value("Deepak Rao"))
                .andExpect(jsonPath("$.status.name").value("Pending Work"))
                .andExpect(jsonPath("$.assignedAt").exists())
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void assign_completedComplaint_returns409() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");
        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/complaints/{id}/complete", id).with(TestAuth.asAdmin()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isConflict());
    }

    @Test
    void reassign_toAnotherCategory_returns400() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");
        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(electricianId))))
                .andExpect(status().isBadRequest());
    }

    // --------------------------------------------------------------- unassign

    @Test
    void unassign_happy_returnsToAssignmentPending() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");
        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/complaints/{id}/unassign", id).with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.name").value("Assignment Pending"))
                .andExpect(jsonPath("$.professional").doesNotExist())
                .andExpect(jsonPath("$.assignedAt").doesNotExist())
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void unassign_thenAssignAgain_works() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");
        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/complaints/{id}/unassign", id).with(TestAuth.asAdmin()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.name").value("Pending Work"));
    }

    @Test
    void unassign_neverAssigned_returns409() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");

        mockMvc.perform(post("/api/v1/complaints/{id}/unassign", id).with(TestAuth.asAdmin()))
                .andExpect(status().isConflict());
    }

    @Test
    void unassign_completedComplaint_returns409() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");
        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/complaints/{id}/complete", id).with(TestAuth.asAdmin()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/complaints/{id}/unassign", id).with(TestAuth.asAdmin()))
                .andExpect(status().isConflict());
    }

    @Test
    void unassign_byResident_returns403() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");
        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/complaints/{id}/unassign", id)
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unassign_unknownComplaint_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/complaints/{id}/unassign", 999_999L).with(TestAuth.asAdmin()))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------------------------- reopen

    /** Assigns and completes a complaint, returning its id. */
    private Long completedComplaint() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");
        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/complaints/{id}/complete", id).with(TestAuth.asAdmin()))
                .andExpect(status().isOk());
        return id;
    }

    @Test
    void reopen_happy_returnsToAssignmentPendingAndClearsWorker() throws Exception {
        Long id = completedComplaint();

        mockMvc.perform(post("/api/v1/complaints/{id}/reopen", id).with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.name").value("Assignment Pending"))
                .andExpect(jsonPath("$.professional").doesNotExist())
                .andExpect(jsonPath("$.assignedAt").doesNotExist())
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void reopen_thenAssignAndCompleteAgain_works() throws Exception {
        Long id = completedComplaint();
        mockMvc.perform(post("/api/v1/complaints/{id}/reopen", id).with(TestAuth.asAdmin()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.name").value("Pending Work"));

        mockMvc.perform(post("/api/v1/complaints/{id}/complete", id).with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.name").value("Complete"));
    }

    @Test
    void reopen_notCompleted_returns409() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");

        mockMvc.perform(post("/api/v1/complaints/{id}/reopen", id).with(TestAuth.asAdmin()))
                .andExpect(status().isConflict());
    }

    @Test
    void reopen_pendingWork_returns409() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");
        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/complaints/{id}/reopen", id).with(TestAuth.asAdmin()))
                .andExpect(status().isConflict());
    }

    @Test
    void reopen_byResident_returns403() throws Exception {
        Long id = completedComplaint();

        mockMvc.perform(post("/api/v1/complaints/{id}/reopen", id)
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat)))
                .andExpect(status().isForbidden());
    }

    @Test
    void reopen_unknownComplaint_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/complaints/{id}/reopen", 999_999L).with(TestAuth.asAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void assign_unknownProfessional_returns404() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");

        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(999_999L))))
                .andExpect(status().isNotFound());
    }

    @Test
    void complete_happy_movesToComplete() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");
        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/complaints/{id}/complete", id).with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.name").value("Complete"))
                .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    void complete_byResident_returns403() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");

        mockMvc.perform(post("/api/v1/complaints/{id}/complete", id)
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat)))
                .andExpect(status().isForbidden());
    }

    @Test
    void complete_unassigned_returns409() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");

        mockMvc.perform(post("/api/v1/complaints/{id}/complete", id).with(TestAuth.asAdmin()))
                .andExpect(status().isConflict());
    }

    @Test
    void complete_alreadyCompleted_returns409() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");
        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/complaints/{id}/complete", id).with(TestAuth.asAdmin()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/complaints/{id}/complete", id).with(TestAuth.asAdmin()))
                .andExpect(status().isConflict());
    }

    @Test
    void complete_unknownComplaint_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/complaints/{id}/complete", 999_999L).with(TestAuth.asAdmin()))
                .andExpect(status().isNotFound());
    }

    // ----------------------------------------------- delete (resident withdraws)

    private String deleteReq(String reason, String comments) {
        return comments == null
                ? String.format("{\"reason\":\"%s\"}", reason)
                : String.format("{\"reason\":\"%s\",\"comments\":\"%s\"}", reason, comments);
    }

    @Test
    void delete_ownPendingComplaint_marksDeletedAndKeepsTheRow() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");

        mockMvc.perform(post("/api/v1/complaints/{id}/delete", id)
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteReq("RESOLVED_ITSELF", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.name").value("Deleted"))
                .andExpect(jsonPath("$.deletionReason").value("RESOLVED_ITSELF"))
                .andExpect(jsonPath("$.deletionReasonLabel").value("Resolved on its own"))
                .andExpect(jsonPath("$.deletedAt").exists());

        // the row survives - that is the whole point of the soft delete
        org.junit.jupiter.api.Assertions.assertTrue(complaintRepository.findById(id).isPresent());
    }

    @Test
    void delete_assignedComplaint_keepsTheWorkerItHadWhenWithdrawn() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");
        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/complaints/{id}/delete", id)
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteReq("HANDLED_PRIVATELY", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status.name").value("Deleted"))
                .andExpect(jsonPath("$.professional.id").value(plumberId))
                .andExpect(jsonPath("$.assignedAt").exists());
    }

    @Test
    void delete_storesFreeTextComments() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");

        mockMvc.perform(post("/api/v1/complaints/{id}/delete", id)
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteReq("OTHER", "Neighbour fixed it for me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletionComments").value("Neighbour fixed it for me"));
    }

    @Test
    void delete_otherWithoutComments_returns400() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");

        mockMvc.perform(post("/api/v1/complaints/{id}/delete", id)
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteReq("OTHER", "   ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_missingReason_returns400() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");

        mockMvc.perform(post("/api/v1/complaints/{id}/delete", id)
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='reason')]").exists());
    }

    @Test
    void delete_unknownReason_returns400() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");

        mockMvc.perform(post("/api/v1/complaints/{id}/delete", id)
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteReq("BECAUSE_I_SAID_SO", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_twice_returns409() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");
        mockMvc.perform(post("/api/v1/complaints/{id}/delete", id)
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteReq("DUPLICATE", null)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/complaints/{id}/delete", id)
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteReq("DUPLICATE", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_completedComplaint_returns409() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");
        mockMvc.perform(post("/api/v1/complaints/{id}/assign", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(assignReq(plumberId))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/complaints/{id}/complete", id).with(TestAuth.asAdmin()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/complaints/{id}/delete", id)
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteReq("RESOLVED_ITSELF", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void delete_anotherFlatsComplaint_returns403() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");

        mockMvc.perform(post("/api/v1/complaints/{id}/delete", id)
                .with(TestAuth.asResident(999L, "Someone Else", "Z-999"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteReq("RESOLVED_ITSELF", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_unknownComplaint_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/complaints/{id}/delete", 999_999L)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteReq("RESOLVED_ITSELF", null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletedComplaint_disappearsFromListings_butIsOnTheDeletedList() throws Exception {
        Long id = createComplaint(Category.PLUMBER, "Leaking sink in kitchen");
        mockMvc.perform(post("/api/v1/complaints/{id}/delete", id)
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(deleteReq("RAISED_BY_MISTAKE", null)))
                .andExpect(status().isOk());

        // admin's full list
        mockMvc.perform(get("/api/v1/complaints").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")]").doesNotExist());

        // the resident's own list
        mockMvc.perform(get("/api/v1/complaints")
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")]").doesNotExist());

        // but it is on the record
        mockMvc.perform(get("/api/v1/complaints/deleted").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")]").exists());
    }

    @Test
    void deletedList_byResident_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/complaints/deleted")
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat)))
                .andExpect(status().isForbidden());
    }
}
