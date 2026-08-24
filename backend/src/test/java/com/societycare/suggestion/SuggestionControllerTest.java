package com.societycare.suggestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.societycare.complaint.Category;
import com.societycare.complaint.ComplaintRepository;
import com.societycare.complaint.dto.CreateComplaintRequest;
import com.societycare.notification.Notification;
import com.societycare.notification.NotificationRepository;
import com.societycare.notification.NotificationService;
import com.societycare.notification.NotificationType;
import com.societycare.notification.RecipientType;
import com.societycare.professional.ProfessionalRepository;
import com.societycare.resident.Resident;
import com.societycare.resident.ResidentRepository;
import com.societycare.suggestion.dto.CreateSuggestionRequest;
import com.societycare.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Resident suggestions, and the management notification each one raises.
 *
 * The read and write sides are deliberately exclusive - residents may only
 * write, management may only read - so most of what is worth asserting here is
 * about who is refused.
 */
@SpringBootTest
@ActiveProfiles("test")
class SuggestionControllerTest {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ResidentRepository residentRepository;
    @Autowired private ProfessionalRepository professionalRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private SuggestionRepository suggestionRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long anitaId;
    private String anitaFlat;
    private Long binaId;
    private String binaFlat;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        // Notifications reference both complaints and suggestions, so they go
        // first - the same ordering ResidentService.delete has to respect.
        notificationRepository.deleteAll();
        complaintRepository.deleteAll();
        suggestionRepository.deleteAll();
        professionalRepository.deleteAll();
        residentRepository.deleteAll();

        Resident anita = new Resident("Anita Sharma", "A-101");
        anita.setPhone("+91 90000 11223");
        anita = residentRepository.save(anita);
        Resident bina = residentRepository.save(new Resident("Bina Rao", "B-202"));

        anitaId = anita.getResidentId();
        anitaFlat = anita.getFlatNo();
        binaId = bina.getResidentId();
        binaFlat = bina.getFlatNo();
    }

    // ------------------------------------------------------------- helpers

    private String suggestionJson(String text) throws Exception {
        CreateSuggestionRequest req = new CreateSuggestionRequest();
        req.setSuggestion(text);
        return objectMapper.writeValueAsString(req);
    }

    private Long submitAsAnita(String text) throws Exception {
        String json = mockMvc.perform(post("/api/v1/suggestions")
                .with(TestAuth.asResident(anitaId, "Anita Sharma", anitaFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(suggestionJson(text)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private Long submitAsBina(String text) throws Exception {
        String json = mockMvc.perform(post("/api/v1/suggestions")
                .with(TestAuth.asResident(binaId, "Bina Rao", binaFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(suggestionJson(text)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    /** Ages a row so "newest first" has something unambiguous to sort. */
    private void backdate(Long suggestionId, int days) {
        jdbcTemplate.update("UPDATE t_suggestion SET created_at = ? WHERE suggestion_id = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(days), suggestionId);
    }

    // -------------------------------------------------------------- create

    @Test
    void create_asResident_returns201AndPersists() throws Exception {
        mockMvc.perform(post("/api/v1/suggestions")
                .with(TestAuth.asResident(anitaId, "Anita Sharma", anitaFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(suggestionJson("The lobby lights stay on all day")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.flatNo").value(anitaFlat))
                .andExpect(jsonPath("$.residentName").value("Anita Sharma"))
                .andExpect(jsonPath("$.suggestion").value("The lobby lights stay on all day"))
                .andExpect(jsonPath("$.createdAt").exists());

        assertThat(suggestionRepository.count()).isEqualTo(1);
    }

    @Test
    void create_surroundingWhitespace_isTrimmed() throws Exception {
        mockMvc.perform(post("/api/v1/suggestions")
                .with(TestAuth.asResident(anitaId, "Anita Sharma", anitaFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(suggestionJson("   Please fix the gate latch   ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.suggestion").value("Please fix the gate latch"));
    }

    @Test
    void create_blank_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/suggestions")
                .with(TestAuth.asResident(anitaId, "Anita Sharma", anitaFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(suggestionJson("   ")))
                .andExpect(status().isBadRequest());

        assertThat(suggestionRepository.count()).isZero();
    }

    @Test
    void create_shorterThanMinimum_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/suggestions")
                .with(TestAuth.asResident(anitaId, "Anita Sharma", anitaFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(suggestionJson("hmm")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_longerThanMaximum_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/suggestions")
                .with(TestAuth.asResident(anitaId, "Anita Sharma", anitaFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(suggestionJson("x".repeat(2001))))
                .andExpect(status().isBadRequest());

        assertThat(suggestionRepository.count()).isZero();
    }

    @Test
    void create_atMaximumLength_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/suggestions")
                .with(TestAuth.asResident(anitaId, "Anita Sharma", anitaFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(suggestionJson("x".repeat(2000))))
                .andExpect(status().isCreated());
    }

    @Test
    void create_asAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/suggestions")
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(suggestionJson("Management has nobody to suggest to")))
                .andExpect(status().isForbidden());

        assertThat(suggestionRepository.count()).isZero();
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/suggestions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(suggestionJson("Anyone at all should not be able to post this")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_alwaysFiledUnderTheCaller_notAnyFlatTheyName() throws Exception {
        // There is no resident id in the request body, so the only flat a
        // suggestion can land under is the caller's own.
        Long id = submitAsAnita("Filed by Anita, and it must stay that way");

        // findAllNewestFirst rather than findById: the resident is a LAZY
        // association and only that query fetches it, so findById would hand
        // back a proxy that blows up the moment this assertion touches it.
        Suggestion saved = suggestionRepository.findAllNewestFirst().get(0);
        assertThat(saved.getSuggestionId()).isEqualTo(id);
        assertThat(saved.getResident().getResidentId()).isEqualTo(anitaId);
        assertThat(saved.getResident().getFlatNo()).isEqualTo(anitaFlat);
    }

    // ---------------------------------------------------------------- list

    @Test
    void list_asAdmin_returnsNewestFirst() throws Exception {
        Long older = submitAsAnita("Written first, so it should come second");
        backdate(older, 2);
        submitAsBina("Written second, so it should come first");

        mockMvc.perform(get("/api/v1/suggestions").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].flatNo").value(binaFlat))
                .andExpect(jsonPath("$[1].flatNo").value(anitaFlat));
    }

    @Test
    void list_asAdmin_carriesTheResidentsContactDetails() throws Exception {
        submitAsAnita("Management may want to call me about this");

        mockMvc.perform(get("/api/v1/suggestions").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].residentName").value("Anita Sharma"))
                .andExpect(jsonPath("$[0].flatNo").value(anitaFlat))
                .andExpect(jsonPath("$[0].residentPhone").value("+91 90000 11223"));
    }

    @Test
    void list_residentWithoutPhone_returnsNullPhoneRatherThanFailing() throws Exception {
        submitAsBina("Bina has no phone number on file");

        mockMvc.perform(get("/api/v1/suggestions").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flatNo").value(binaFlat))
                .andExpect(jsonPath("$[0].residentPhone").doesNotExist());
    }

    @Test
    void list_asResident_returns403() throws Exception {
        submitAsAnita("Even my own suggestion is not readable back to me");

        mockMvc.perform(get("/api/v1/suggestions")
                .with(TestAuth.asResident(anitaId, "Anita Sharma", anitaFlat)))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/suggestions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_noneRaised_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/v1/suggestions").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void list_severalFromOneFlat_keepsThemAll() throws Exception {
        submitAsAnita("First thought about the parking");
        submitAsAnita("Second thought about the parking");

        mockMvc.perform(get("/api/v1/suggestions").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].flatNo").value(anitaFlat))
                .andExpect(jsonPath("$[1].flatNo").value(anitaFlat));
    }

    // -------------------------------------------------------- notification

    @Test
    void create_notifiesManagement() throws Exception {
        submitAsAnita("The gate latch is loose again");

        List<Notification> all = notificationRepository.findAll();
        assertThat(all).hasSize(1);
        Notification n = all.get(0);
        assertThat(n.getType()).isEqualTo(NotificationType.SUGGESTION_RAISED);
        assertThat(n.getRecipientType()).isEqualTo(RecipientType.ADMIN);
        assertThat(n.getMessage()).isEqualTo("Flat " + anitaFlat + " shared a new suggestion");
        // Addressed to management as a whole, so no individual resident, and its
        // subject is the suggestion rather than a complaint.
        assertThat(n.getResident()).isNull();
        assertThat(n.getComplaint()).isNull();
        assertThat(n.getSuggestion()).isNotNull();
    }

    @Test
    void create_showsUpInTheManagementFeed() throws Exception {
        Long suggestionId = submitAsAnita("Please add a notice board near the lift");

        mockMvc.perform(get("/api/v1/notifications").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("SUGGESTION_RAISED"))
                .andExpect(jsonPath("$[0].message").value("Flat " + anitaFlat + " shared a new suggestion"))
                .andExpect(jsonPath("$[0].suggestionId").value(suggestionId))
                .andExpect(jsonPath("$[0].flatNo").value(anitaFlat))
                // A suggestion is not filed against a trade, so there is no
                // category for the panel to show.
                .andExpect(jsonPath("$[0].category").doesNotExist())
                .andExpect(jsonPath("$[0].complaintId").doesNotExist())
                .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    void create_doesNotNotifyTheResidentWhoWroteIt() throws Exception {
        submitAsAnita("This should not come back to me as a notification");

        mockMvc.perform(get("/api/v1/notifications")
                .with(TestAuth.asResident(anitaId, "Anita Sharma", anitaFlat)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void create_raisesTheManagementUnreadCount() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread-count").with(TestAuth.asAdmin()))
                .andExpect(jsonPath("$.count").value(0));

        submitAsAnita("One suggestion should move the count by one");

        mockMvc.perform(get("/api/v1/notifications/unread-count").with(TestAuth.asAdmin()))
                .andExpect(jsonPath("$.count").value(1));
    }

    /**
     * Regression guard for V7. A notification is now about a complaint *or* a
     * suggestion, and the feed query fetches both - an inner join on either one
     * would silently drop every row of the other kind.
     */
    @Test
    void managementFeed_withBothKinds_returnsComplaintAndSuggestionRows() throws Exception {
        CreateComplaintRequest complaint = new CreateComplaintRequest();
        complaint.setCategory(Category.PLUMBER);
        complaint.setDescription("Leaking sink in the kitchen");
        mockMvc.perform(post("/api/v1/complaints")
                .with(TestAuth.asResident(binaId, "Bina Rao", binaFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(complaint)))
                .andExpect(status().isCreated());

        submitAsAnita("And a suggestion alongside it");

        mockMvc.perform(get("/api/v1/notifications").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.type == 'SUGGESTION_RAISED')].flatNo").value(anitaFlat))
                .andExpect(jsonPath("$[?(@.type == 'COMPLAINT_RAISED')].flatNo").value(binaFlat));
    }

    /**
     * The 45-day retention sweep clears the notification but must leave the
     * suggestion itself alone. Management stops being nudged about it; the
     * feedback stays on the suggestions page, which is the record.
     */
    @Test
    void retentionSweep_clearsTheNotificationButKeepsTheSuggestion() throws Exception {
        submitAsAnita("This should outlive the notification about it");
        Long notificationId = notificationRepository.findAll().get(0).getNotificationId();
        jdbcTemplate.update("UPDATE t_notification SET created_at = ? WHERE notification_id = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(46), notificationId);

        int removed = notificationService.purgeOlderThan(
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(45));

        assertThat(removed).isEqualTo(1);
        assertThat(notificationRepository.count()).isZero();
        assertThat(suggestionRepository.count()).isEqualTo(1);
    }

    // ------------------------------------------------------ resident delete

    /**
     * Suggestions carry a non-null foreign key to the resident, and their
     * notifications carry one to the suggestion. Deleting a resident has to
     * unwind all three in order or the database refuses.
     */
    @Test
    void deleteResident_takesTheirSuggestionsAndNotificationsWithThem() throws Exception {
        submitAsAnita("Anita will be removed after writing this");
        submitAsBina("Bina stays, and so should this");

        assertThat(suggestionRepository.count()).isEqualTo(2);
        assertThat(notificationRepository.count()).isEqualTo(2);

        mockMvc.perform(delete("/api/v1/admin/residents/" + anitaId).with(TestAuth.asAdmin()))
                .andExpect(status().isNoContent());

        assertThat(suggestionRepository.count()).isEqualTo(1);
        assertThat(suggestionRepository.findAllNewestFirst().get(0).getResident().getFlatNo())
                .isEqualTo(binaFlat);
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void deleteResident_withNoSuggestions_stillSucceeds() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/residents/" + binaId).with(TestAuth.asAdmin()))
                .andExpect(status().isNoContent());

        assertThat(residentRepository.findById(binaId)).isEmpty();
    }
}
