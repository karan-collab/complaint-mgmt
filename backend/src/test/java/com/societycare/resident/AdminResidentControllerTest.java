package com.societycare.resident;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.societycare.resident.dto.CreateResidentRequest;
import com.societycare.resident.dto.ResetPasswordRequest;
import com.societycare.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
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
class AdminResidentControllerTest {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ResidentRepository residentRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        residentRepository.deleteAll();
    }

    private CreateResidentRequest req(String name, String flat, String password) {
        CreateResidentRequest r = new CreateResidentRequest();
        r.setName(name);
        r.setFlatNo(flat);
        r.setPassword(password);
        return r;
    }

    @Test
    void create_happy_storesBcryptHash() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents")
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req("Anita Sharma", "A-101", "pass123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flatNo").value("A-101"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        Resident saved = residentRepository.findByFlatNoIgnoreCase("A-101").orElseThrow();
        // Hash must verify, and must NOT equal the raw password.
        org.junit.jupiter.api.Assertions.assertNotEquals("pass123", saved.getPasswordHash());
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches("pass123", saved.getPasswordHash()));
    }

    @Test
    void create_duplicateFlat_returns409() throws Exception {
        residentRepository.save(new Resident("Existing", "A-101"));

        mockMvc.perform(post("/api/v1/admin/residents")
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req("Anita Sharma", "A-101", "pass123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void create_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents")
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req("Anita", "A-101", "x"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists());
    }

    @Test
    void create_byResident_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents")
                .with(TestAuth.asResident("A-101"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req("Anita Sharma", "B-202", "pass123"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_byResident_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/residents").with(TestAuth.asResident("A-101")))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_byAdmin_returnsResidents() throws Exception {
        residentRepository.save(new Resident("Anita", "A-101"));
        residentRepository.save(new Resident("Ravi", "B-202"));

        mockMvc.perform(get("/api/v1/admin/residents").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    private ResetPasswordRequest resetReq(String newPassword) {
        ResetPasswordRequest r = new ResetPasswordRequest();
        r.setNewPassword(newPassword);
        return r;
    }

    @Test
    void resetPassword_byAdmin_happy_residentCanLoginWithNewPassword() throws Exception {
        Resident anita = new Resident("Anita Sharma", "A-101");
        anita.setPasswordHash(passwordEncoder.encode("oldOne"));
        Long id = residentRepository.save(anita).getResidentId();

        mockMvc.perform(post("/api/v1/admin/residents/{id}/password", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(resetReq("newSecret"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/resident/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"flatNo\":\"A-101\",\"password\":\"oldOne\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/resident/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"flatNo\":\"A-101\",\"password\":\"newSecret\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_byResident_returns403() throws Exception {
        Long id = residentRepository.save(new Resident("Anita", "A-101")).getResidentId();

        mockMvc.perform(post("/api/v1/admin/residents/{id}/password", id)
                .with(TestAuth.asResident("A-101"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(resetReq("newSecret"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void resetPassword_unknownResident_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/admin/residents/{id}/password", 999_999L)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(resetReq("newSecret"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void resetPassword_shortNew_returns400() throws Exception {
        Long id = residentRepository.save(new Resident("Anita", "A-101")).getResidentId();

        mockMvc.perform(post("/api/v1/admin/residents/{id}/password", id)
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(resetReq("x"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='newPassword')]").exists());
    }
}
