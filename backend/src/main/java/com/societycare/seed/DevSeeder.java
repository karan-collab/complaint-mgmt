package com.societycare.seed;

import com.societycare.admin.Admin;
import com.societycare.admin.AdminRepository;
import com.societycare.complaint.Category;
import com.societycare.complaint.Complaint;
import com.societycare.complaint.ComplaintRepository;
import com.societycare.professional.Professional;
import com.societycare.professional.ProfessionalRepository;
import com.societycare.resident.Resident;
import com.societycare.resident.ResidentRepository;
import com.societycare.status.Status;
import com.societycare.status.StatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * Dev-only data seeder. Mirrors buildSeed() in scripts/storage.js so that
 * once the frontend is wired to the API (Batch 4) it sees the same complaints
 * with the same relative timestamps as the current localStorage demo.
 *
 * Active only when the "dev" profile is enabled AND seed.enabled=true.
 * Skips entirely if any complaints already exist.
 */
@Component
@Profile("dev")
public class DevSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeeder.class);

    private final boolean seedEnabled;
    private final ResidentRepository residentRepository;
    private final ProfessionalRepository professionalRepository;
    private final ComplaintRepository complaintRepository;
    private final StatusRepository statusRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    public DevSeeder(@Value("${seed.enabled:false}") boolean seedEnabled,
                     ResidentRepository residentRepository,
                     ProfessionalRepository professionalRepository,
                     ComplaintRepository complaintRepository,
                     StatusRepository statusRepository,
                     AdminRepository adminRepository,
                     PasswordEncoder passwordEncoder) {
        this.seedEnabled = seedEnabled;
        this.residentRepository = residentRepository;
        this.professionalRepository = professionalRepository;
        this.complaintRepository = complaintRepository;
        this.statusRepository = statusRepository;
        this.adminRepository = adminRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedEnabled) {
            log.info("DevSeeder disabled (seed.enabled=false)");
            return;
        }
        if (complaintRepository.count() > 0) {
            log.info("DevSeeder skipped: complaints already exist");
            return;
        }

        log.info("DevSeeder running: inserting residents, professionals and complaints");

        Map<String, Resident> residents = seedResidents();
        Map<String, Professional> pros = seedProfessionals();
        Status assignmentPending = statusRepository.getReferenceById(Status.ASSIGNMENT_PENDING);
        Status pendingWork = statusRepository.getReferenceById(Status.PENDING_WORK);
        Status complete = statusRepository.getReferenceById(Status.COMPLETE);

        seedAdminIfMissing();

        // 1. A-101 Plumber, completed 7 days ago, raised 10 days ago.
        save(complaint(residents.get("A-101"), Category.PLUMBER,
                "Kitchen sink leaking from the bottom; water collecting under the cabinet.",
                complete, pros.get("Suresh Patel"),
                daysAgo(10), daysAgo(8), daysAgo(7)));

        // 2. A-101 Electrician, raised 1 day ago, no worker yet.
        save(complaint(residents.get("A-101"), Category.ELECTRICIAN,
                "Living room ceiling fan stopped working since last night.",
                assignmentPending, null,
                daysAgo(1), null, null));

        // 3. A-101 Carpenter, raised 2 days ago, assigned 1 day ago.
        save(complaint(residents.get("A-101"), Category.CARPENTER,
                "Bedroom door hinge is loose; door does not shut properly.",
                pendingWork, pros.get("Ramesh Kumar"),
                daysAgo(2), daysAgo(1), null));

        // 4. B-202 Plumber, raised 6 hours ago, no worker.
        save(complaint(residents.get("B-202"), Category.PLUMBER,
                "Bathroom tap drips continuously even when fully closed.",
                assignmentPending, null,
                hoursAgo(6), null, null));

        // 5. B-202 Electrician, raised 3 days ago, assigned 2 days ago.
        save(complaint(residents.get("B-202"), Category.ELECTRICIAN,
                "Power socket in the study room is sparking when plugged in.",
                pendingWork, pros.get("Anil Yadav"),
                daysAgo(3), daysAgo(2), null));

        // 6. C-303 Painting, completed.
        save(complaint(residents.get("C-303"), Category.PAINTING,
                "Living room wall paint peeling after last monsoon - touch-up requested.",
                complete, pros.get("Mahesh Solanki"),
                daysAgo(20), daysAgo(15), daysAgo(12)));

        // 7. D-404 Carpenter, raised 2 days ago, no worker.
        save(complaint(residents.get("D-404"), Category.CARPENTER,
                "Kitchen drawer slider broken; drawer comes off when pulled.",
                assignmentPending, null,
                daysAgo(2), null, null));

        // 8. E-505 Plumber, raised 2 hours ago, no worker.
        save(complaint(residents.get("E-505"), Category.PLUMBER,
                "Low water pressure in master bathroom shower; only a trickle in the morning.",
                assignmentPending, null,
                hoursAgo(2), null, null));

        log.info("DevSeeder finished. Residents={}, Professionals={}, Complaints={}",
                residentRepository.count(), professionalRepository.count(), complaintRepository.count());
    }

    private Map<String, Resident> seedResidents() {
        // Dev-only credentials: every seeded resident logs in with password "pass123".
        String defaultHash = passwordEncoder.encode("pass123");
        Map<String, Resident> map = new HashMap<>();
        map.put("A-101", saveResident("Anita Sharma", "A-101", defaultHash));
        map.put("B-202", saveResident("Ravi Mehta", "B-202", defaultHash));
        map.put("C-303", saveResident("Priya Kapoor", "C-303", defaultHash));
        map.put("D-404", saveResident("Vikram Singh", "D-404", defaultHash));
        map.put("E-505", saveResident("Neha Iyer", "E-505", defaultHash));
        return map;
    }

    private Resident saveResident(String name, String flatNo, String passwordHash) {
        Resident r = new Resident(name, flatNo);
        r.setPasswordHash(passwordHash);
        return residentRepository.save(r);
    }

    private Map<String, Professional> seedProfessionals() {
        Map<String, Professional> map = new HashMap<>();
        map.put("Suresh Patel",
                professionalRepository.save(new Professional("Suresh Patel", "+91 90000 11223", Category.PLUMBER)));
        map.put("Ramesh Kumar",
                professionalRepository.save(new Professional("Ramesh Kumar", "+91 98765 43210", Category.CARPENTER)));
        map.put("Anil Yadav",
                professionalRepository.save(new Professional("Anil Yadav", "+91 99887 76655", Category.ELECTRICIAN)));
        map.put("Mahesh Solanki",
                professionalRepository.save(new Professional("Mahesh Solanki", "+91 90909 80808", Category.PAINTING)));
        return map;
    }

    /**
     * Seeds a default {@code admin / admin} account if none exists.
     * Idempotent: running the seeder a second time leaves an existing admin alone.
     */
    private void seedAdminIfMissing() {
        if (!adminRepository.findByUsername("admin").isPresent()) {
            adminRepository.save(new Admin("admin", passwordEncoder.encode("admin")));
        }
    }

    private Complaint complaint(Resident resident, Category category, String description,
                                Status status, Professional professional,
                                OffsetDateTime createdAt, OffsetDateTime assignedAt, OffsetDateTime completedAt) {
        Complaint c = new Complaint();
        c.setResident(resident);
        c.setCategory(category);
        c.setDescription(description);
        c.setStatus(status);
        c.setProfessional(professional);
        c.setCreatedAt(createdAt);
        c.setAssignedAt(assignedAt);
        c.setCompletedAt(completedAt);
        return c;
    }

    private void save(Complaint c) {
        complaintRepository.save(c);
    }

    private static OffsetDateTime daysAgo(int days) {
        return OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
    }

    private static OffsetDateTime hoursAgo(int hours) {
        return OffsetDateTime.now(ZoneOffset.UTC).minusHours(hours);
    }
}
