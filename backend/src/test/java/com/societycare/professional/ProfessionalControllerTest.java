package com.societycare.professional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.societycare.complaint.Category;
import com.societycare.complaint.Complaint;
import com.societycare.complaint.ComplaintRepository;
import com.societycare.professional.dto.CreateProfessionalRequest;
import com.societycare.professional.dto.UpdateProfessionalRequest;
import com.societycare.resident.Resident;
import com.societycare.resident.ResidentRepository;
import com.societycare.status.Status;
import com.societycare.status.StatusRepository;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ProfessionalControllerTest {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ProfessionalRepository professionalRepository;
    @Autowired private ResidentRepository residentRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private StatusRepository statusRepository;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        complaintRepository.deleteAll();
        professionalRepository.deleteAll();
        residentRepository.deleteAll();
    }

    private CreateProfessionalRequest createReq(String name, String phone, Category category) {
        CreateProfessionalRequest r = new CreateProfessionalRequest();
        r.setName(name);
        r.setPhone(phone);
        r.setCategory(category);
        return r;
    }

    @Test
    void create_happy_returns201AndPersists() throws Exception {
        mockMvc.perform(post("/api/v1/professionals")
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(
                        createReq("Asha Pillai", "+91 90000 00000", Category.ELECTRICIAN))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Asha Pillai"))
                .andExpect(jsonPath("$.category").value("ELECTRICIAN"));
    }

    @Test
    void create_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/professionals")
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(createReq("", "+91 1234567890", Category.PLUMBER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists());
    }

    @Test
    void create_byResident_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/professionals")
                .with(TestAuth.asResident("A-101"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(
                        createReq("Asha Pillai", "+91 90000 00000", Category.ELECTRICIAN))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_filterByCategory_returnsOnlyMatching() throws Exception {
        professionalRepository.save(new Professional("P1", "+91 1", Category.PLUMBER));
        professionalRepository.save(new Professional("P2", "+91 2", Category.PLUMBER));
        professionalRepository.save(new Professional("E1", "+91 3", Category.ELECTRICIAN));

        mockMvc.perform(get("/api/v1/professionals").param("category", "PLUMBER")
                        .with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void list_byResident_returns403() throws Exception {
        professionalRepository.save(new Professional("P1", "+91 1", Category.PLUMBER));
        mockMvc.perform(get("/api/v1/professionals").with(TestAuth.asResident("A-101")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_byResident_returns403() throws Exception {
        Professional saved = professionalRepository.save(
                new Professional("Solo", "+91 1", Category.CARPENTER));
        mockMvc.perform(get("/api/v1/professionals/{id}", saved.getProfessionalId())
                        .with(TestAuth.asResident("A-101")))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_noFilter_returnsAllSortedByCategoryThenName() throws Exception {
        professionalRepository.save(new Professional("Zed", "+91 1", Category.PLUMBER));
        professionalRepository.save(new Professional("Aaron", "+91 2", Category.PLUMBER));
        professionalRepository.save(new Professional("Bob", "+91 3", Category.ELECTRICIAN));

        mockMvc.perform(get("/api/v1/professionals").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                // CARPENTER < ELECTRICIAN < ... < PLUMBER alphabetically; ELECTRICIAN comes before PLUMBER
                .andExpect(jsonPath("$[0].category").value("ELECTRICIAN"))
                .andExpect(jsonPath("$[1].name").value("Aaron"));
    }

    @Test
    void update_happy_changesNameAndPhone() throws Exception {
        Professional saved = professionalRepository.save(
                new Professional("Old Name", "+91 1", Category.PLUMBER));

        UpdateProfessionalRequest r = new UpdateProfessionalRequest();
        r.setName("New Name");
        r.setPhone("+91 9999");

        mockMvc.perform(patch("/api/v1/professionals/{id}", saved.getProfessionalId())
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(r)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"))
                .andExpect(jsonPath("$.phone").value("+91 9999"))
                .andExpect(jsonPath("$.category").value("PLUMBER"));
    }

    @Test
    void update_unknown_returns404() throws Exception {
        UpdateProfessionalRequest r = new UpdateProfessionalRequest();
        r.setName("x");
        mockMvc.perform(patch("/api/v1/professionals/{id}", 999_999L)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(r)))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_unreferenced_succeeds() throws Exception {
        Professional saved = professionalRepository.save(
                new Professional("Solo", "+91 1", Category.CARPENTER));

        mockMvc.perform(delete("/api/v1/professionals/{id}", saved.getProfessionalId())
                        .with(TestAuth.asAdmin()))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_byResident_returns403() throws Exception {
        Professional saved = professionalRepository.save(
                new Professional("Solo", "+91 1", Category.CARPENTER));

        mockMvc.perform(delete("/api/v1/professionals/{id}", saved.getProfessionalId())
                        .with(TestAuth.asResident("A-101")))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_referenced_returns409() throws Exception {
        Resident resident = residentRepository.save(new Resident("Anita", "A-101"));
        Professional pro = professionalRepository.save(
                new Professional("Suresh", "+91 1", Category.PLUMBER));

        Complaint c = new Complaint();
        c.setResident(resident);
        c.setCategory(Category.PLUMBER);
        c.setDescription("test");
        c.setStatus(statusRepository.getReferenceById(Status.PENDING_WORK));
        c.setProfessional(pro);
        c.setAssignedAt(OffsetDateTime.now(ZoneOffset.UTC));
        complaintRepository.save(c);

        mockMvc.perform(delete("/api/v1/professionals/{id}", pro.getProfessionalId())
                        .with(TestAuth.asAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }
}
