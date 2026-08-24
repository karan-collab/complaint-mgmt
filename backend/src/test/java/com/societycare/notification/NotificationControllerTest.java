package com.societycare.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.societycare.complaint.Category;
import com.societycare.complaint.ComplaintRepository;
import com.societycare.complaint.DeletionReason;
import com.societycare.complaint.dto.AssignComplaintRequest;
import com.societycare.complaint.dto.CreateComplaintRequest;
import com.societycare.complaint.dto.DeleteComplaintRequest;
import com.societycare.professional.Professional;
import com.societycare.professional.ProfessionalRepository;
import com.societycare.resident.Resident;
import com.societycare.resident.ResidentRepository;
import com.societycare.suggestion.SuggestionRepository;
import com.societycare.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The bell feed. "Refresh" in these tests is literally what the browser does on
 * a poll: GET /api/v1/notifications (or /unread-count) as the signed-in user.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ResidentRepository residentRepository;
    @Autowired private SuggestionRepository suggestionRepository;
    @Autowired private ProfessionalRepository professionalRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long anitaId;
    private String anitaFlat;
    private Long binaId;
    private String binaFlat;
    private Long plumberId;
    private Long otherPlumberId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // Notifications reference complaints, so they go first - the same
        // ordering ResidentService.delete has to respect.
        notificationRepository.deleteAll();
        complaintRepository.deleteAll();
        professionalRepository.deleteAll();
        suggestionRepository.deleteAll();
        residentRepository.deleteAll();

        Resident anita = residentRepository.save(new Resident("Anita Sharma", "A-101"));
        Resident bina = residentRepository.save(new Resident("Bina Rao", "B-202"));
        Professional plumber = professionalRepository.save(
                new Professional("Suresh Patel", "+91 90000 11223", Category.PLUMBER));
        Professional otherPlumber = professionalRepository.save(
                new Professional("Ramesh Iyer", "+91 90000 44556", Category.PLUMBER));

        anitaId = anita.getResidentId();
        anitaFlat = anita.getFlatNo();
        binaId = bina.getResidentId();
        binaFlat = bina.getFlatNo();
        plumberId = plumber.getProfessionalId();
        otherPlumberId = otherPlumber.getProfessionalId();
    }

    // ------------------------------------------------------------- helpers

    private Long createComplaint(Long residentId, String name, String flat, String description)
            throws Exception {
        CreateComplaintRequest req = new CreateComplaintRequest();
        req.setCategory(Category.PLUMBER);
        req.setDescription(description);
        String json = mockMvc.perform(post("/api/v1/complaints")
                .with(TestAuth.asResident(residentId, name, flat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private Long createComplaintForAnita() throws Exception {
        return createComplaint(anitaId, "Anita Sharma", anitaFlat, "Leaking sink in kitchen");
    }

    private void assign(Long complaintId, Long professionalId) throws Exception {
        AssignComplaintRequest req = new AssignComplaintRequest();
        req.setProfessionalId(professionalId);
        mockMvc.perform(post("/api/v1/complaints/" + complaintId + "/assign")
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isOk());
    }

    private void transition(Long complaintId, String action) throws Exception {
        mockMvc.perform(post("/api/v1/complaints/" + complaintId + "/" + action)
                .with(TestAuth.asAdmin()))
                .andExpect(status().isOk());
    }

    private void withdraw(Long complaintId, Long residentId, String name, String flat)
            throws Exception {
        DeleteComplaintRequest req = new DeleteComplaintRequest();
        req.setReason(DeletionReason.RESOLVED_ITSELF);
        mockMvc.perform(post("/api/v1/complaints/" + complaintId + "/delete")
                .with(TestAuth.asResident(residentId, name, flat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isOk());
    }

    /** What the admin's browser fetches on a refresh/poll. */
    private ResultActions adminFeed() throws Exception {
        return mockMvc.perform(get("/api/v1/notifications").with(TestAuth.asAdmin()))
                .andExpect(status().isOk());
    }

    private ResultActions residentFeed(Long residentId, String name, String flat) throws Exception {
        return mockMvc.perform(get("/api/v1/notifications")
                .with(TestAuth.asResident(residentId, name, flat)))
                .andExpect(status().isOk());
    }

    private ResultActions anitaFeed() throws Exception {
        return residentFeed(anitaId, "Anita Sharma", anitaFlat);
    }

    // ------------------------------------------- admin sees resident actions

    @Test
    void createComplaint_onRefresh_adminSeesNotification() throws Exception {
        createComplaintForAnita();

        adminFeed()
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("COMPLAINT_RAISED"))
                .andExpect(jsonPath("$[0].flatNo").value("A-101"))
                .andExpect(jsonPath("$[0].category").value("Plumber"))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].message").value("Flat A-101 raised a new Plumber issue"));
    }

    @Test
    void withdrawComplaint_onRefresh_adminSeesNotification() throws Exception {
        Long complaintId = createComplaintForAnita();
        withdraw(complaintId, anitaId, "Anita Sharma", anitaFlat);

        adminFeed()
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.type=='COMPLAINT_WITHDRAWN')]").exists())
                .andExpect(jsonPath("$[?(@.message=='Flat A-101 withdrew a Plumber issue')]").exists());
    }

    @Test
    void withdrawComplaint_vowelCategory_usesAnNotA() throws Exception {
        CreateComplaintRequest req = new CreateComplaintRequest();
        req.setCategory(Category.ELECTRICIAN);
        req.setDescription("Sparking socket in the hall");
        String json = mockMvc.perform(post("/api/v1/complaints")
                .with(TestAuth.asResident(anitaId, "Anita Sharma", anitaFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long complaintId = objectMapper.readTree(json).get("id").asLong();

        withdraw(complaintId, anitaId, "Anita Sharma", anitaFlat);

        adminFeed().andExpect(jsonPath(
                "$[?(@.message=='Flat A-101 withdrew an Electrician issue')]").exists());
    }

    // ------------------------------------------- resident sees admin actions

    @Test
    void assign_onRefresh_residentSeesNotification() throws Exception {
        Long complaintId = createComplaintForAnita();
        assign(complaintId, plumberId);

        anitaFeed()
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("WORKER_ASSIGNED"))
                .andExpect(jsonPath("$[0].complaintId").value(complaintId))
                .andExpect(jsonPath("$[0].message")
                        .value("Suresh Patel (Plumber) has been assigned to your Plumber complaint"));
    }

    @Test
    void complete_onRefresh_residentSeesNotification() throws Exception {
        Long complaintId = createComplaintForAnita();
        assign(complaintId, plumberId);
        transition(complaintId, "complete");

        anitaFeed()
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.type=='COMPLAINT_COMPLETED')]").exists())
                .andExpect(jsonPath("$[?(@.message=='Your Plumber complaint has been marked complete')]")
                        .exists());
    }

    @Test
    void unassign_onRefresh_residentSeesNotification() throws Exception {
        Long complaintId = createComplaintForAnita();
        assign(complaintId, plumberId);
        transition(complaintId, "unassign");

        anitaFeed()
                .andExpect(jsonPath("$[?(@.type=='WORKER_REMOVED')]").exists())
                .andExpect(jsonPath("$[?(@.message =~ /Suresh Patel was removed.*/)]").exists());
    }

    @Test
    void reopen_onRefresh_residentSeesNotification() throws Exception {
        Long complaintId = createComplaintForAnita();
        assign(complaintId, plumberId);
        transition(complaintId, "complete");
        transition(complaintId, "reopen");

        anitaFeed()
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.type=='COMPLAINT_REOPENED')]").exists());
    }

    @Test
    void assign_toDifferentWorker_recordsReassignmentNotRepeatAssignment() throws Exception {
        Long complaintId = createComplaintForAnita();
        assign(complaintId, plumberId);
        assign(complaintId, otherPlumberId);

        anitaFeed()
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.type=='WORKER_REASSIGNED')]").exists())
                .andExpect(jsonPath("$[?(@.message=='Your Plumber complaint has been reassigned to "
                        + "Ramesh Iyer (Plumber)')]").exists());
    }

    // -------------------------------------------------------- separation

    @Test
    void residentFeed_anotherFlatsEvents_areNotVisible() throws Exception {
        Long binaComplaint = createComplaint(binaId, "Bina Rao", binaFlat, "No water in bathroom");
        assign(binaComplaint, plumberId);

        // Bina's assignment must not reach Anita, who has nothing of her own.
        anitaFeed().andExpect(jsonPath("$.length()").value(0));

        residentFeed(binaId, "Bina Rao", binaFlat)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].flatNo").value("B-202"));
    }

    @Test
    void adminFeed_residentFacingEvents_areNotIncluded() throws Exception {
        Long complaintId = createComplaintForAnita();
        assign(complaintId, plumberId);
        transition(complaintId, "complete");

        // Only the raise is management's business; the assign/complete messages
        // are addressed to the resident and are written in the second person.
        adminFeed()
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("COMPLAINT_RAISED"));
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------- unread / read

    @Test
    void unreadCount_countsOnlyOwnUnread() throws Exception {
        Long complaintId = createComplaintForAnita();
        assign(complaintId, plumberId);
        transition(complaintId, "complete");

        mockMvc.perform(get("/api/v1/notifications/unread-count").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                .with(TestAuth.asResident(anitaId, "Anita Sharma", anitaFlat)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void markRead_clearsOwnUnreadCountOnly() throws Exception {
        Long complaintId = createComplaintForAnita();
        assign(complaintId, plumberId);

        mockMvc.perform(post("/api/v1/notifications/read")
                .with(TestAuth.asResident(anitaId, "Anita Sharma", anitaFlat)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                .with(TestAuth.asResident(anitaId, "Anita Sharma", anitaFlat)))
                .andExpect(jsonPath("$.count").value(0));
        anitaFeed().andExpect(jsonPath("$[0].read").value(true));

        // The admin's own unread raise notification is untouched.
        mockMvc.perform(get("/api/v1/notifications/unread-count").with(TestAuth.asAdmin()))
                .andExpect(jsonPath("$.count").value(1));
    }

    // --------------------------------------------------------- housekeeping

    @Test
    void deleteResident_removesTheirNotificationsFromTheTable() throws Exception {
        Long complaintId = createComplaintForAnita();
        assign(complaintId, plumberId);
        // One ADMIN row (raised) and one RESIDENT row (assigned), both tied to
        // a complaint that is about to be hard-deleted with the resident.
        assertThat(notificationRepository.count()).isEqualTo(2);

        mockMvc.perform(delete("/api/v1/admin/residents/" + anitaId).with(TestAuth.asAdmin()))
                .andExpect(status().isNoContent());

        assertThat(notificationRepository.count()).isZero();
        assertThat(complaintRepository.count()).isZero();
    }

    @Test
    void deleteResident_leavesOtherResidentsNotificationsAlone() throws Exception {
        Long anitaComplaint = createComplaintForAnita();
        assign(anitaComplaint, plumberId);
        Long binaComplaint = createComplaint(binaId, "Bina Rao", binaFlat, "No water in bathroom");
        assign(binaComplaint, otherPlumberId);
        assertThat(notificationRepository.count()).isEqualTo(4);

        mockMvc.perform(delete("/api/v1/admin/residents/" + anitaId).with(TestAuth.asAdmin()))
                .andExpect(status().isNoContent());

        assertThat(notificationRepository.count()).isEqualTo(2);
        residentFeed(binaId, "Bina Rao", binaFlat).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void purge_notificationsOlderThanRetentionWindow_areRemoved() throws Exception {
        Long complaintId = createComplaintForAnita();
        assign(complaintId, plumberId);
        assertThat(notificationRepository.count()).isEqualTo(2);

        // Age the raise notification past the 45-day window. created_at is
        // updatable=false on the entity, so this goes in through SQL.
        Long oldestId = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.COMPLAINT_RAISED)
                .findFirst().orElseThrow().getNotificationId();
        jdbcTemplate.update("UPDATE t_notification SET created_at = ? WHERE notification_id = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(46), oldestId);

        int removed = notificationService.purgeOlderThan(
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(45));

        assertThat(removed).isEqualTo(1);
        assertThat(notificationRepository.count()).isEqualTo(1);
        adminFeed().andExpect(jsonPath("$.length()").value(0));
        anitaFeed().andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void purge_notificationsInsideRetentionWindow_areKept() throws Exception {
        Long complaintId = createComplaintForAnita();
        assign(complaintId, plumberId);

        int removed = notificationService.purgeOlderThan(
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(45));

        assertThat(removed).isZero();
        assertThat(notificationRepository.count()).isEqualTo(2);
    }
}
