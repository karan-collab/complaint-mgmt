package com.societycare.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.societycare.complaint.Category;
import com.societycare.complaint.ComplaintRepository;
import com.societycare.complaint.dto.CreateComplaintRequest;
import com.societycare.notification.NotificationRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A timestamp written by the application must come back out as the same instant.
 *
 * This is not as obvious as it sounds. The entities store OffsetDateTime into
 * "TIMESTAMP WITH TIME ZONE" columns, and a JDBC timezone conversion layered on
 * top of a column that already carries its own offset will shift every value by
 * the JVM's offset - silently, and only visibly on a machine that is not on UTC.
 *
 * These assertions fail loudly on such a setup, which a date-only display would
 * never reveal.
 */
@SpringBootTest
@ActiveProfiles("test")
class TimestampRoundTripTest {

    /** Generous: this is catching hour-scale timezone shifts, not clock drift. */
    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ResidentRepository residentRepository;
    @Autowired private SuggestionRepository suggestionRepository;
    @Autowired private ProfessionalRepository professionalRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long residentId;
    private String residentFlat;

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
        residentId = anita.getResidentId();
        residentFlat = anita.getFlatNo();
    }

    private Long createComplaint() throws Exception {
        CreateComplaintRequest req = new CreateComplaintRequest();
        req.setCategory(Category.PLUMBER);
        req.setDescription("Leaking sink in kitchen");
        String json = mockMvc.perform(post("/api/v1/complaints")
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    @Test
    void complaintCreatedAt_roundTripsAsTheInstantItWasWritten() throws Exception {
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
        Long complaintId = createComplaint();
        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC);

        // Deliberately a second request: the POST response hands back the entity
        // still in memory, so it would report the right value even when what
        // reached the database was shifted. Only a fresh read proves the trip.
        String json = mockMvc.perform(get("/api/v1/complaints/" + complaintId)
                .with(TestAuth.asResident(residentId, "Anita Sharma", residentFlat)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        OffsetDateTime createdAt =
                OffsetDateTime.parse(objectMapper.readTree(json).get("createdAt").asText());

        assertThat(createdAt)
                .as("createdAt reported by the API should be the moment the row was written")
                .isBetween(before.minus(TOLERANCE), after.plus(TOLERANCE));
    }

    /**
     * The bell shows relative times ("2 minutes ago"), so a shifted timestamp is
     * immediately visible to users here even though it hides on a date-only view.
     */
    @Test
    void notificationCreatedAt_roundTripsAsTheInstantItWasWritten() throws Exception {
        OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);
        createComplaint();
        OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC);

        OffsetDateTime createdAt = notificationRepository.findAll().get(0).getCreatedAt();

        assertThat(createdAt)
                .as("notification createdAt drives the bell's relative timestamps")
                .isBetween(before.minus(TOLERANCE), after.plus(TOLERANCE));
    }
}
