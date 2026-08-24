package com.societycare.complaint;

import com.societycare.notification.NotificationRepository;
import com.societycare.professional.Professional;
import com.societycare.professional.ProfessionalRepository;
import com.societycare.resident.Resident;
import com.societycare.resident.ResidentRepository;
import com.societycare.status.Status;
import com.societycare.status.StatusRepository;
import com.societycare.suggestion.SuggestionRepository;
import com.societycare.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ComplaintControllerTest {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ResidentRepository residentRepository;
    @Autowired private SuggestionRepository suggestionRepository;
    @Autowired private ProfessionalRepository professionalRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private StatusRepository statusRepository;

    private MockMvc mockMvc;

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

        Resident anitaEntity = new Resident("Anita Sharma", "A-101");
        anitaEntity.setPhone("+91 98000 12345");
        Resident anita = residentRepository.save(anitaEntity);
        Resident ravi = residentRepository.save(new Resident("Ravi Mehta", "B-202"));

        Professional plumber =
                professionalRepository.save(new Professional("Suresh Patel", "+91 90000 11223", Category.PLUMBER));

        Status pending = statusRepository.getReferenceById(Status.ASSIGNMENT_PENDING);
        Status pendingWork = statusRepository.getReferenceById(Status.PENDING_WORK);
        Status complete = statusRepository.getReferenceById(Status.COMPLETE);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        complaintRepository.save(make(anita, Category.PLUMBER, "leak",
                complete, plumber,
                now.minusDays(10), now.minusDays(8), now.minusDays(7)));
        complaintRepository.save(make(anita, Category.ELECTRICIAN, "fan dead",
                pending, null,
                now.minusDays(1), null, null));
        complaintRepository.save(make(anita, Category.CARPENTER, "loose hinge",
                pendingWork, plumberAs(Category.CARPENTER),
                now.minusDays(2), now.minusDays(1), null));
        complaintRepository.save(make(ravi, Category.PLUMBER, "drip",
                pending, null,
                now.minusHours(6), null, null));
    }

    private Professional plumberAs(Category category) {
        return professionalRepository.save(new Professional("Ramesh Kumar", "+91 98765 43210", category));
    }

    private Complaint make(Resident r, Category category, String description,
                           Status status, Professional pro,
                           OffsetDateTime created, OffsetDateTime assigned, OffsetDateTime completed) {
        Complaint c = new Complaint();
        c.setResident(r);
        c.setCategory(category);
        c.setDescription(description);
        c.setStatus(status);
        c.setProfessional(pro);
        c.setCreatedAt(created);
        c.setAssignedAt(assigned);
        c.setCompletedAt(completed);
        return c;
    }

    @Test
    void listAll_returnsAllComplaintsSortedByCreatedAtDesc() throws Exception {
        mockMvc.perform(get("/api/v1/complaints").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].description").value("drip"));
    }

    @Test
    void listByFlat_returnsOnlyMatchingFlat() throws Exception {
        mockMvc.perform(get("/api/v1/complaints").param("flat", "A-101").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].flatNo").value("A-101"));
    }

    @Test
    void listByFlat_isCaseInsensitive() throws Exception {
        mockMvc.perform(get("/api/v1/complaints").param("flat", "a-101").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void getById_returnsComplaint() throws Exception {
        Long id = complaintRepository.findAll().get(0).getComplaintId();
        mockMvc.perform(get("/api/v1/complaints/{id}", id).with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void getById_unknownReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/complaints/{id}", 999_999L).with(TestAuth.asAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void completedComplaint_hasCompletedStatusAndProfessional() throws Exception {
        mockMvc.perform(get("/api/v1/complaints").param("flat", "A-101").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.description=='leak')].status.name").value("Complete"))
                .andExpect(jsonPath("$[?(@.description=='leak')].professional.name").value("Suresh Patel"));
    }

    @Test
    void unassignedComplaint_hasNoProfessional() throws Exception {
        // JSONPath filter expressions return an array; an unassigned complaint
        // therefore yields [null], so we assert "single-element list containing null".
        mockMvc.perform(get("/api/v1/complaints").param("flat", "A-101").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.description=='fan dead')].professional",
                        contains(nullValue())))
                .andExpect(jsonPath("$[?(@.description=='fan dead')].status.name").value("Assignment Pending"));
    }

    @Test
    void resident_seesOnlyOwnFlatComplaints() throws Exception {
        mockMvc.perform(get("/api/v1/complaints").with(TestAuth.asResident("A-101")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void resident_cannotPassDifferentFlat() throws Exception {
        mockMvc.perform(get("/api/v1/complaints").param("flat", "B-202").with(TestAuth.asResident("A-101")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    @Test
    void resident_cannotReadOtherFlatsComplaintById() throws Exception {
        // The unique seeded B-202 complaint has description "drip".
        Long otherFlatComplaintId = complaintRepository.findAll().stream()
                .filter(c -> "drip".equals(c.getDescription()))
                .findFirst()
                .orElseThrow()
                .getComplaintId();

        mockMvc.perform(get("/api/v1/complaints/{id}", otherFlatComplaintId)
                        .with(TestAuth.asResident("A-101")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_isRejectedAs401() throws Exception {
        mockMvc.perform(get("/api/v1/complaints"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthenticated"));
    }

    @Test
    void list_includesFlatOwnerNameAndPhone() throws Exception {
        mockMvc.perform(get("/api/v1/complaints?flat=A-101").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].residentName").value("Anita Sharma"))
                .andExpect(jsonPath("$[0].residentPhone").value("+91 98000 12345"));
    }

    @Test
    void list_residentWithoutPhone_omitsIt() throws Exception {
        mockMvc.perform(get("/api/v1/complaints?flat=B-202").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].residentName").value("Ravi Mehta"))
                .andExpect(jsonPath("$[0].residentPhone").doesNotExist());
    }
}
