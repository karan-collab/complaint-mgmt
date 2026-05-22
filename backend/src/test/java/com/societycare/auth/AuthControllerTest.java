package com.societycare.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.societycare.admin.Admin;
import com.societycare.admin.AdminRepository;
import com.societycare.auth.dto.AdminLoginRequest;
import com.societycare.auth.dto.ChangePasswordRequest;
import com.societycare.auth.dto.ResidentLoginRequest;
import com.societycare.resident.Resident;
import com.societycare.resident.ResidentRepository;
import com.societycare.security.JwtService;
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
class AuthControllerTest {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ResidentRepository residentRepository;
    @Autowired private AdminRepository adminRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        residentRepository.deleteAll();
        adminRepository.deleteAll();

        Resident anita = new Resident("Anita Sharma", "A-101");
        anita.setPasswordHash(passwordEncoder.encode("pass123"));
        residentRepository.save(anita);

        adminRepository.save(new Admin("admin", passwordEncoder.encode("admin")));
    }

    @Test
    void residentLogin_happy_returnsTokenAndProfile() throws Exception {
        ResidentLoginRequest req = new ResidentLoginRequest();
        req.setFlatNo("A-101");
        req.setPassword("pass123");

        mockMvc.perform(post("/api/v1/auth/resident/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.role").value("RESIDENT"))
                .andExpect(jsonPath("$.flatNo").value("A-101"))
                .andExpect(jsonPath("$.expiresInSeconds").value(120 * 60));
    }

    @Test
    void residentLogin_caseInsensitiveFlat() throws Exception {
        ResidentLoginRequest req = new ResidentLoginRequest();
        req.setFlatNo("a-101");
        req.setPassword("pass123");

        mockMvc.perform(post("/api/v1/auth/resident/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isOk());
    }

    @Test
    void residentLogin_wrongPassword_returns401() throws Exception {
        ResidentLoginRequest req = new ResidentLoginRequest();
        req.setFlatNo("A-101");
        req.setPassword("WRONG");

        mockMvc.perform(post("/api/v1/auth/resident/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthenticated"));
    }

    @Test
    void residentLogin_unknownFlat_returns401() throws Exception {
        ResidentLoginRequest req = new ResidentLoginRequest();
        req.setFlatNo("Z-999");
        req.setPassword("anything");

        mockMvc.perform(post("/api/v1/auth/resident/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void residentLogin_blankFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/resident/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"flatNo\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void adminLogin_happy_returnsTokenAndProfile() throws Exception {
        AdminLoginRequest req = new AdminLoginRequest();
        req.setUsername("admin");
        req.setPassword("admin");

        mockMvc.perform(post("/api/v1/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.flatNo").doesNotExist());
    }

    @Test
    void adminLogin_wrongPassword_returns401() throws Exception {
        AdminLoginRequest req = new AdminLoginRequest();
        req.setUsername("admin");
        req.setPassword("WRONG");

        mockMvc.perform(post("/api/v1/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withResidentToken_returnsResidentProfile() throws Exception {
        Resident anita = residentRepository.findByFlatNoIgnoreCase("A-101").orElseThrow();
        String token = jwtService.issueForResident(anita.getResidentId(), anita.getResidentName(), anita.getFlatNo());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("RESIDENT"))
                .andExpect(jsonPath("$.flatNo").value("A-101"))
                .andExpect(jsonPath("$.displayName").value("Anita Sharma"));
    }

    @Test
    void me_withAdminToken_returnsAdminProfile() throws Exception {
        Admin admin = adminRepository.findByUsername("admin").orElseThrow();
        String token = jwtService.issueForAdmin(admin.getAdminId(), admin.getUsername());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.displayName").value("admin"));
    }

    @Test
    void me_withGarbageToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not.a.valid.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withNoToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    private ChangePasswordRequest changeReq(String current, String next) {
        ChangePasswordRequest r = new ChangePasswordRequest();
        r.setCurrentPassword(current);
        r.setNewPassword(next);
        return r;
    }

    @Test
    void changeMyPassword_resident_happy_canLoginWithNewPassword() throws Exception {
        Resident anita = residentRepository.findByFlatNoIgnoreCase("A-101").orElseThrow();

        mockMvc.perform(post("/api/v1/auth/me/password")
                .with(TestAuth.asResident(anita.getResidentId(), anita.getResidentName(), anita.getFlatNo()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(changeReq("pass123", "newPass456"))))
                .andExpect(status().isNoContent());

        // Old password no longer works.
        mockMvc.perform(post("/api/v1/auth/resident/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"flatNo\":\"A-101\",\"password\":\"pass123\"}"))
                .andExpect(status().isUnauthorized());

        // New password does.
        mockMvc.perform(post("/api/v1/auth/resident/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"flatNo\":\"A-101\",\"password\":\"newPass456\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void changeMyPassword_wrongCurrent_returns400_keepsSession() throws Exception {
        // A wrong current password is a form-input problem, NOT a session/JWT problem.
        // It MUST return 400 so the client doesn't mistakenly log the user out.
        Resident anita = residentRepository.findByFlatNoIgnoreCase("A-101").orElseThrow();

        mockMvc.perform(post("/api/v1/auth/me/password")
                .with(TestAuth.asResident(anita.getResidentId(), anita.getResidentName(), anita.getFlatNo()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(changeReq("WRONG", "newPass456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Current password is incorrect"));
    }

    @Test
    void changeMyPassword_sameAsOld_returns400() throws Exception {
        Resident anita = residentRepository.findByFlatNoIgnoreCase("A-101").orElseThrow();

        mockMvc.perform(post("/api/v1/auth/me/password")
                .with(TestAuth.asResident(anita.getResidentId(), anita.getResidentName(), anita.getFlatNo()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(changeReq("pass123", "pass123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changeMyPassword_shortNew_returns400() throws Exception {
        Resident anita = residentRepository.findByFlatNoIgnoreCase("A-101").orElseThrow();

        mockMvc.perform(post("/api/v1/auth/me/password")
                .with(TestAuth.asResident(anita.getResidentId(), anita.getResidentName(), anita.getFlatNo()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(changeReq("pass123", "x"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='newPassword')]").exists());
    }

    @Test
    void changeMyPassword_anonymous_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(changeReq("pass123", "newPass456"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changeMyPassword_admin_happy() throws Exception {
        Admin admin = adminRepository.findByUsername("admin").orElseThrow();

        mockMvc.perform(post("/api/v1/auth/me/password")
                .with(TestAuth.asAdmin(admin.getAdminId(), admin.getUsername()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(changeReq("admin", "newAdminPw"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"newAdminPw\"}"))
                .andExpect(status().isOk());
    }
}
