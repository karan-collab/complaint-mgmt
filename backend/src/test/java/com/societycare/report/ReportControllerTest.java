package com.societycare.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.societycare.complaint.Category;
import com.societycare.complaint.ComplaintRepository;
import com.societycare.complaint.DeletionReason;
import com.societycare.complaint.dto.AssignComplaintRequest;
import com.societycare.complaint.dto.CreateComplaintRequest;
import com.societycare.complaint.dto.DeleteComplaintRequest;
import com.societycare.notification.NotificationRepository;
import com.societycare.professional.Professional;
import com.societycare.professional.ProfessionalRepository;
import com.societycare.resident.Resident;
import com.societycare.resident.ResidentRepository;
import com.societycare.suggestion.SuggestionRepository;
import com.societycare.suggestion.dto.CreateSuggestionRequest;
import com.societycare.support.TestAuth;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The management activity report.
 *
 * Everything here reads the produced workbook back with POI rather than
 * trusting the service that built it. A report is the kind of feature whose
 * bugs are invisible - a boundary that drops the last day, or a column that
 * quietly shifts - so the assertions are made against the actual bytes a
 * manager would open.
 *
 * The range used throughout is March 2026, deliberately in the past, so that
 * "today" can never wander into it and make a test flaky.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportControllerTest {

    /** Must match app.reports.time-zone; the boundaries are Indian calendar days. */
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private ResidentRepository residentRepository;
    @Autowired private ProfessionalRepository professionalRepository;
    @Autowired private ComplaintRepository complaintRepository;
    @Autowired private SuggestionRepository suggestionRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Long anitaId;
    private String anitaFlat;
    private Long binaId;
    private String binaFlat;
    private Long plumberId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        notificationRepository.deleteAll();
        complaintRepository.deleteAll();
        suggestionRepository.deleteAll();
        professionalRepository.deleteAll();
        residentRepository.deleteAll();

        Resident anita = new Resident("Anita Sharma", "A-101");
        anita.setPhone("+91 90000 11223");
        anita = residentRepository.save(anita);
        Resident bina = residentRepository.save(new Resident("Bina Rao", "B-202"));
        Professional plumber = professionalRepository.save(
                new Professional("Suresh Patel", "+91 90000 44556", Category.PLUMBER));

        anitaId = anita.getResidentId();
        anitaFlat = anita.getFlatNo();
        binaId = bina.getResidentId();
        binaFlat = bina.getFlatNo();
        plumberId = plumber.getProfessionalId();
    }

    // ------------------------------------------------------------- fixtures

    private Long raiseComplaint(Long residentId, String name, String flat,
                                Category category, String description) throws Exception {
        CreateComplaintRequest req = new CreateComplaintRequest();
        req.setCategory(category);
        req.setDescription(description);
        String json = mockMvc.perform(post("/api/v1/complaints")
                .with(TestAuth.asResident(residentId, name, flat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private Long anitaComplaint(String description) throws Exception {
        return raiseComplaint(anitaId, "Anita Sharma", anitaFlat, Category.PLUMBER, description);
    }

    private Long binaSuggestion(String text) throws Exception {
        CreateSuggestionRequest req = new CreateSuggestionRequest();
        req.setSuggestion(text);
        String json = mockMvc.perform(post("/api/v1/suggestions")
                .with(TestAuth.asResident(binaId, "Bina Rao", binaFlat))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private void assign(Long complaintId) throws Exception {
        AssignComplaintRequest req = new AssignComplaintRequest();
        req.setProfessionalId(plumberId);
        mockMvc.perform(post("/api/v1/complaints/" + complaintId + "/assign")
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isOk());
    }

    private void complete(Long complaintId) throws Exception {
        mockMvc.perform(post("/api/v1/complaints/" + complaintId + "/complete")
                .with(TestAuth.asAdmin()))
                .andExpect(status().isOk());
    }

    private void withdraw(Long complaintId) throws Exception {
        DeleteComplaintRequest req = new DeleteComplaintRequest();
        req.setReason(DeletionReason.RESOLVED_ITSELF);
        mockMvc.perform(post("/api/v1/complaints/" + complaintId + "/delete")
                .with(TestAuth.asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(req)))
                .andExpect(status().isOk());
    }

    /**
     * Timestamps come from the entity's @PrePersist, so the only way to place a
     * record at a chosen instant is to move it afterwards. Every boundary test
     * below depends on this.
     */
    private void setComplaintRaisedAt(Long complaintId, OffsetDateTime when) {
        jdbcTemplate.update("UPDATE t_complaint SET created_at = ? WHERE complaint_id = ?",
                when, complaintId);
    }

    private void setSuggestionRaisedAt(Long suggestionId, OffsetDateTime when) {
        jdbcTemplate.update("UPDATE t_suggestion SET created_at = ? WHERE suggestion_id = ?",
                when, suggestionId);
    }

    private void setCompletedAt(Long complaintId, OffsetDateTime when) {
        jdbcTemplate.update("UPDATE t_complaint SET completed_at = ? WHERE complaint_id = ?",
                when, complaintId);
    }

    /** An instant expressed as Indian wall-clock time. */
    private static OffsetDateTime ist(int year, int month, int day, int hour, int minute, int second) {
        return LocalDateTime.of(year, month, day, hour, minute, second).atZone(IST).toOffsetDateTime();
    }

    // -------------------------------------------------------------- calling

    private byte[] download(LocalDate from, LocalDate to) throws Exception {
        return mockMvc.perform(get("/api/v1/reports/activity")
                .param("from", from.toString())
                .param("to", to.toString())
                .with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private byte[] downloadMarch() throws Exception {
        return download(FROM, TO);
    }

    // ------------------------------------------------------- workbook access

    private static XSSFWorkbook open(byte[] bytes) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static int columnIndex(String header) {
        int i = Arrays.asList(ActivityReportWriter.HEADERS).indexOf(header);
        assertThat(i).as("column '" + header + "' exists").isNotNegative();
        return i;
    }

    private static Cell cell(Row row, String header) {
        return row.getCell(columnIndex(header));
    }

    /** Cell text, with a genuinely blank cell reported as "". */
    private static String text(Row row, String header) {
        Cell c = cell(row, header);
        if (c == null || c.getCellType() == CellType.BLANK) return "";
        if (c.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) c.getNumericCellValue());
        }
        return c.getStringCellValue();
    }

    private static List<Row> dataRows(XSSFWorkbook wb) {
        List<Row> rows = new ArrayList<>();
        // Row 0 is the header.
        for (int i = 1; i <= wb.getSheetAt(0).getLastRowNum(); i++) {
            Row r = wb.getSheetAt(0).getRow(i);
            if (r != null) rows.add(r);
        }
        return rows;
    }

    private static List<String> columnValues(XSSFWorkbook wb, String header) {
        List<String> values = new ArrayList<>();
        for (Row r : dataRows(wb)) values.add(text(r, header));
        return values;
    }

    // -------------------------------------------------------- access control

    @Test
    void download_asAdmin_returnsAnXlsxAttachment() throws Exception {
        mockMvc.perform(get("/api/v1/reports/activity")
                .param("from", FROM.toString()).param("to", TO.toString())
                .with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String type = result.getResponse().getContentType();
                    assertThat(type).startsWith(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                });
    }

    @Test
    void download_asResident_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports/activity")
                .param("from", FROM.toString()).param("to", TO.toString())
                .with(TestAuth.asResident(anitaId, "Anita Sharma", anitaFlat)))
                .andExpect(status().isForbidden());
    }

    @Test
    void download_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/reports/activity"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void download_namesTheFileAfterTheDateRange() throws Exception {
        String disposition = mockMvc.perform(get("/api/v1/reports/activity")
                .param("from", FROM.toString()).param("to", TO.toString())
                .with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);

        assertThat(disposition)
                .isEqualTo("attachment; filename=\"societycare-report-2026-03-01-to-2026-03-31.xlsx\"");
    }

    @Test
    void download_isNeverCached() throws Exception {
        // A report is a snapshot; a cached copy served after new tickets land
        // would be quietly out of date.
        String cacheControl = mockMvc.perform(get("/api/v1/reports/activity")
                .param("from", FROM.toString()).param("to", TO.toString())
                .with(TestAuth.asAdmin()))
                .andReturn().getResponse().getHeader(HttpHeaders.CACHE_CONTROL);

        assertThat(cacheControl).contains("no-store");
    }

    // --------------------------------------------------------------- shape

    @Test
    void report_hasTheExpectedColumnsInOrder() throws Exception {
        try (XSSFWorkbook wb = open(downloadMarch())) {
            Row header = wb.getSheetAt(0).getRow(0);
            List<String> actual = new ArrayList<>();
            for (int i = 0; i < ActivityReportWriter.HEADERS.length; i++) {
                actual.add(header.getCell(i).getStringCellValue());
            }
            assertThat(actual).containsExactly(
                    "Type", "Flat", "Resident", "Category", "Complaint",
                    "Suggestion", "Status", "Assigned To", "Raised On",
                    "Assigned On", "Completed On");
            // Nothing beyond the last header, so a stray column cannot creep in.
            assertThat(header.getLastCellNum()).isEqualTo((short) ActivityReportWriter.HEADERS.length);
        }
    }

    @Test
    void emptyRange_stillReturnsAWorkbookWithJustTheHeaders() throws Exception {
        try (XSSFWorkbook wb = open(downloadMarch())) {
            // Cast because POI's Row is Iterable<Cell>, which makes a bare
            // assertThat(row) ambiguous between AssertJ's object and iterable
            // overloads.
            assertThat((Object) wb.getSheetAt(0).getRow(0)).isNotNull();
            assertThat(dataRows(wb)).isEmpty();
        }
    }

    @Test
    void report_headerRowIsFrozenSoItSurvivesScrolling() throws Exception {
        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(wb.getSheetAt(0).getPaneInformation()).isNotNull();
            assertThat(wb.getSheetAt(0).getPaneInformation().getHorizontalSplitPosition())
                    .isEqualTo((short) 1);
        }
    }

    // ------------------------------------------------------ date boundaries

    @Test
    void range_includesTheFirstInstantOfTheFromDay() throws Exception {
        Long id = anitaComplaint("Raised at the stroke of midnight");
        setComplaintRaisedAt(id, ist(2026, 3, 1, 0, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(columnValues(wb, "Complaint"))
                    .containsExactly("Raised at the stroke of midnight");
        }
    }

    @Test
    void range_includesTheLastInstantOfTheToDay() throws Exception {
        // The classic off-by-one: an inclusive "<= 23:59:59" bound silently
        // drops anything raised in the final second of the closing day.
        Long id = anitaComplaint("Raised one second before midnight");
        setComplaintRaisedAt(id, ist(2026, 3, 31, 23, 59, 59));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(columnValues(wb, "Complaint"))
                    .containsExactly("Raised one second before midnight");
        }
    }

    @Test
    void range_excludesTheInstantBeforeTheFromDay() throws Exception {
        Long id = anitaComplaint("Raised just before the window opens");
        setComplaintRaisedAt(id, ist(2026, 2, 28, 23, 59, 59));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(dataRows(wb)).isEmpty();
        }
    }

    @Test
    void range_excludesTheFirstInstantAfterTheToDay() throws Exception {
        Long id = anitaComplaint("Raised just after the window closes");
        setComplaintRaisedAt(id, ist(2026, 4, 1, 0, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(dataRows(wb)).isEmpty();
        }
    }

    @Test
    void range_ofASingleDay_returnsOnlyThatDay() throws Exception {
        Long onTheDay = anitaComplaint("On the day itself");
        setComplaintRaisedAt(onTheDay, ist(2026, 3, 15, 12, 0, 0));
        Long dayBefore = anitaComplaint("The day before");
        setComplaintRaisedAt(dayBefore, ist(2026, 3, 14, 12, 0, 0));
        Long dayAfter = anitaComplaint("The day after");
        setComplaintRaisedAt(dayAfter, ist(2026, 3, 16, 12, 0, 0));

        try (XSSFWorkbook wb = open(download(LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 15)))) {
            assertThat(columnValues(wb, "Complaint")).containsExactly("On the day itself");
        }
    }

    @Test
    void range_isResolvedInTheReportZoneNotUtc() throws Exception {
        // 2026-03-31 20:00 UTC is 2026-04-01 01:30 in India, so this belongs to
        // April and must NOT appear in the March report. Resolved against UTC
        // it would wrongly be included.
        Long id = anitaComplaint("Raised at half past one in the morning, Indian time");
        setComplaintRaisedAt(id, OffsetDateTime.of(2026, 3, 31, 20, 0, 0, 0, ZoneOffset.UTC));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(dataRows(wb)).isEmpty();
        }
    }

    @Test
    void range_filtersOnTheRaisedDateNotTheCompletedDate() throws Exception {
        // Raised inside the window, finished long after it: belongs to March.
        Long raisedInMarch = anitaComplaint("Raised in March, finished in May");
        assign(raisedInMarch);
        complete(raisedInMarch);
        setComplaintRaisedAt(raisedInMarch, ist(2026, 3, 10, 9, 0, 0));
        setCompletedAt(raisedInMarch, ist(2026, 5, 2, 9, 0, 0));

        // Raised before the window, finished inside it: does NOT belong.
        Long raisedInFebruary = anitaComplaint("Raised in February, finished in March");
        assign(raisedInFebruary);
        complete(raisedInFebruary);
        setComplaintRaisedAt(raisedInFebruary, ist(2026, 2, 10, 9, 0, 0));
        setCompletedAt(raisedInFebruary, ist(2026, 3, 12, 9, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(columnValues(wb, "Complaint"))
                    .containsExactly("Raised in March, finished in May");
        }
    }

    @Test
    void fromAfterTo_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/reports/activity")
                .param("from", "2026-03-31").param("to", "2026-03-01")
                .with(TestAuth.asAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unparseableDate_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/reports/activity")
                .param("from", "31-03-2026").param("to", "2026-03-31")
                .with(TestAuth.asAdmin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noDatesGiven_defaultsToThisMonthSoFar() throws Exception {
        Long thisMonth = anitaComplaint("Raised today");
        Long lastMonth = anitaComplaint("Raised before this month began");
        LocalDate endOfLastMonth = LocalDate.now(IST).withDayOfMonth(1).minusDays(1);
        setComplaintRaisedAt(lastMonth,
                endOfLastMonth.atTime(12, 0).atZone(IST).toOffsetDateTime());

        byte[] bytes = mockMvc.perform(get("/api/v1/reports/activity").with(TestAuth.asAdmin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook wb = open(bytes)) {
            assertThat(columnValues(wb, "Complaint")).containsExactly("Raised today");
        }
    }

    // ------------------------------------------------------ column contents

    @Test
    void complaintRow_carriesEveryColumnItShould() throws Exception {
        Long id = raiseComplaint(anitaId, "Anita Sharma", anitaFlat,
                Category.PLUMBER, "Leaking sink in the kitchen");
        assign(id);
        complete(id);
        setComplaintRaisedAt(id, ist(2026, 3, 5, 10, 0, 0));
        setCompletedAt(id, ist(2026, 3, 7, 16, 30, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            Row row = dataRows(wb).get(0);
            assertThat(text(row, "Type")).isEqualTo("Complaint");
            assertThat(text(row, "Flat")).isEqualTo(anitaFlat);
            assertThat(text(row, "Resident")).isEqualTo("Anita Sharma");
            assertThat(text(row, "Category")).isEqualTo("Plumber");
            assertThat(text(row, "Complaint")).isEqualTo("Leaking sink in the kitchen");
            assertThat(text(row, "Status")).isEqualTo("Completed");
            assertThat(text(row, "Assigned To")).isEqualTo("Suresh Patel");
            // A complaint is not a suggestion; that column stays empty.
            assertThat(text(row, "Suggestion")).isEmpty();
        }
    }

    @Test
    void suggestionRow_leavesTheComplaintOnlyColumnsBlank() throws Exception {
        Long id = binaSuggestion("A bicycle rack near the play area would help");
        setSuggestionRaisedAt(id, ist(2026, 3, 8, 11, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            Row row = dataRows(wb).get(0);
            assertThat(text(row, "Type")).isEqualTo("Suggestion");
            assertThat(text(row, "Flat")).isEqualTo(binaFlat);
            assertThat(text(row, "Resident")).isEqualTo("Bina Rao");
            assertThat(text(row, "Suggestion"))
                    .isEqualTo("A bicycle rack near the play area would help");
            // A suggestion is not filed against a trade, has no lifecycle and
            // nobody is sent out for it.
            assertThat(text(row, "Category")).isEmpty();
            assertThat(text(row, "Complaint")).isEmpty();
            assertThat(text(row, "Status")).isEmpty();
            assertThat(text(row, "Assigned To")).isEmpty();
            assertThat(text(row, "Assigned On")).isEmpty();
            assertThat(text(row, "Completed On")).isEmpty();
        }
    }

    @Test
    void blankCellsAreGenuinelyBlank_notEmptyStrings() throws Exception {
        // COUNTBLANK in Excel counts absent cells, not cells holding "". If we
        // wrote empty strings the sheet would look blank but count as full.
        Long id = binaSuggestion("Nothing here should be a stray empty string");
        setSuggestionRaisedAt(id, ist(2026, 3, 8, 11, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            Row row = dataRows(wb).get(0);
            for (String header : List.of("Category", "Complaint", "Status",
                    "Assigned To", "Assigned On", "Completed On")) {
                Cell c = cell(row, header);
                assertThat(c == null || c.getCellType() == CellType.BLANK)
                        .as(header + " is a blank cell").isTrue();
            }
        }
    }

    @Test
    void unassignedComplaint_hasNoWorkerAndNoAssignedDate() throws Exception {
        Long id = anitaComplaint("Nobody has been sent out yet");
        setComplaintRaisedAt(id, ist(2026, 3, 9, 10, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            Row row = dataRows(wb).get(0);
            assertThat(text(row, "Status")).isEqualTo("Assignment Pending");
            assertThat(text(row, "Assigned To")).isEmpty();
            assertThat(text(row, "Assigned On")).isEmpty();
            assertThat(text(row, "Completed On")).isEmpty();
        }
    }

    @Test
    void assignedButUnfinishedComplaint_hasAWorkerAndNoCompletion() throws Exception {
        Long id = anitaComplaint("Somebody is on their way");
        assign(id);
        setComplaintRaisedAt(id, ist(2026, 3, 9, 10, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            Row row = dataRows(wb).get(0);
            assertThat(text(row, "Status")).isEqualTo("Pending Work");
            assertThat(text(row, "Assigned To")).isEqualTo("Suresh Patel");
            assertThat(cell(row, "Assigned On")).isNotNull();
            assertThat(text(row, "Completed On")).isEmpty();
        }
    }

    @Test
    void withdrawnComplaint_isLeftOutOfTheReport() throws Exception {
        // A complaint the resident pulled back is not work the society did, so
        // the report hides it exactly as every screen does.
        Long id = anitaComplaint("Sorted itself out in the end");
        withdraw(id);
        setComplaintRaisedAt(id, ist(2026, 3, 11, 10, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(dataRows(wb)).isEmpty();
        }
    }

    @Test
    void withdrawnComplaint_doesNotDisturbTheRowsAroundIt() throws Exception {
        // Guards against the exclusion being applied after the rows are built,
        // which would leave a hole or drop a neighbour instead of one row.
        Long before = anitaComplaint("Raised before the withdrawn one");
        setComplaintRaisedAt(before, ist(2026, 3, 10, 10, 0, 0));
        Long pulled = anitaComplaint("This one gets pulled back");
        withdraw(pulled);
        setComplaintRaisedAt(pulled, ist(2026, 3, 11, 10, 0, 0));
        Long after = anitaComplaint("Raised after the withdrawn one");
        setComplaintRaisedAt(after, ist(2026, 3, 12, 10, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(columnValues(wb, "Complaint")).containsExactly(
                    "Raised before the withdrawn one", "Raised after the withdrawn one");
        }
    }

    @Test
    void withdrawingAComplaint_removesItFromASubsequentReport() throws Exception {
        Long id = anitaComplaint("Here today, withdrawn tomorrow");
        setComplaintRaisedAt(id, ist(2026, 3, 11, 10, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(dataRows(wb)).hasSize(1);
        }

        withdraw(id);

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(dataRows(wb)).isEmpty();
        }
    }

    @Test
    void everyStatus_readsAsItDoesOnScreen() throws Exception {
        Long pending = anitaComplaint("Waiting for an assignment");
        setComplaintRaisedAt(pending, ist(2026, 3, 1, 1, 0, 0));

        Long working = anitaComplaint("Work in progress");
        assign(working);
        setComplaintRaisedAt(working, ist(2026, 3, 1, 2, 0, 0));

        Long done = anitaComplaint("All finished");
        assign(done);
        complete(done);
        setComplaintRaisedAt(done, ist(2026, 3, 1, 3, 0, 0));

        // Withdrawn is deliberately absent: those rows never reach the sheet.
        Long gone = anitaComplaint("Pulled back by the resident");
        withdraw(gone);
        setComplaintRaisedAt(gone, ist(2026, 3, 1, 4, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(columnValues(wb, "Status")).containsExactly(
                    "Assignment Pending", "Pending Work", "Completed");
        }
    }

    @Test
    void everyCategory_isWrittenWithItsDisplayLabel() throws Exception {
        Long electrical = raiseComplaint(anitaId, "Anita Sharma", anitaFlat,
                Category.ELECTRICIAN, "Broken wire");
        setComplaintRaisedAt(electrical, ist(2026, 3, 2, 9, 0, 0));
        Long painting = raiseComplaint(anitaId, "Anita Sharma", anitaFlat,
                Category.PAINTING, "Peeling paint");
        setComplaintRaisedAt(painting, ist(2026, 3, 2, 10, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(columnValues(wb, "Category")).containsExactly("Electrician", "Painting");
        }
    }

    @Test
    void report_carriesComplaintsAndSuggestionsTogether() throws Exception {
        Long complaint = anitaComplaint("A complaint");
        setComplaintRaisedAt(complaint, ist(2026, 3, 4, 9, 0, 0));
        Long suggestion = binaSuggestion("A suggestion, sent the following day");
        setSuggestionRaisedAt(suggestion, ist(2026, 3, 5, 9, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(columnValues(wb, "Type")).containsExactly("Complaint", "Suggestion");
            assertThat(columnValues(wb, "Flat")).containsExactly(anitaFlat, binaFlat);
        }
    }

    @Test
    void rows_areOrderedOldestFirst() throws Exception {
        Long third = anitaComplaint("Raised last");
        setComplaintRaisedAt(third, ist(2026, 3, 20, 9, 0, 0));
        Long first = anitaComplaint("Raised first");
        setComplaintRaisedAt(first, ist(2026, 3, 2, 9, 0, 0));
        Long second = binaSuggestion("Raised in between");
        setSuggestionRaisedAt(second, ist(2026, 3, 10, 9, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(columnValues(wb, "Complaint"))
                    .containsExactly("Raised first", "", "Raised last");
            assertThat(columnValues(wb, "Suggestion"))
                    .containsExactly("", "Raised in between", "");
        }
    }

    @Test
    void longDescription_survivesIntact() throws Exception {
        String long2000 = "x".repeat(2000);
        Long id = anitaComplaint(long2000);
        setComplaintRaisedAt(id, ist(2026, 3, 6, 9, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(text(dataRows(wb).get(0), "Complaint")).isEqualTo(long2000);
        }
    }

    @Test
    void textWithCommasAndNewlines_survivesIntact() throws Exception {
        // The reason this is a workbook and not a CSV: no escaping to get wrong.
        String awkward = "Leaking, dripping; then \"flooding\"\nand it spread to the hall";
        Long id = anitaComplaint(awkward);
        setComplaintRaisedAt(id, ist(2026, 3, 6, 9, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(text(dataRows(wb).get(0), "Complaint")).isEqualTo(awkward);
        }
    }

    @Test
    void residentPhoneNumbers_appearNowhereInTheReport() throws Exception {
        // Deliberate: the export gets forwarded around, so personal contact
        // details stay out of it.
        Long id = anitaComplaint("Anita has a phone number on file");
        setComplaintRaisedAt(id, ist(2026, 3, 6, 9, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            for (Row row : dataRows(wb)) {
                for (int i = 0; i < ActivityReportWriter.HEADERS.length; i++) {
                    Cell c = row.getCell(i);
                    if (c != null && c.getCellType() == CellType.STRING) {
                        assertThat(c.getStringCellValue()).doesNotContain("90000 11223");
                    }
                }
            }
        }
    }

    @Test
    void report_carriesNoTicketNumbers() throws Exception {
        // Deliberately dropped: the sheet is for analysis, and an internal id
        // is not something management asked to see.
        Long id = anitaComplaint("There should be no ticket number beside this");
        setComplaintRaisedAt(id, ist(2026, 3, 6, 9, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(Arrays.asList(ActivityReportWriter.HEADERS)).doesNotContain("ID");
            for (Row row : dataRows(wb)) {
                for (int i = 0; i < ActivityReportWriter.HEADERS.length; i++) {
                    Cell c = row.getCell(i);
                    // Nothing numeric survives except the date columns.
                    if (c != null && c.getCellType() == CellType.NUMERIC) {
                        assertThat(DateUtil.isCellDateFormatted(c))
                                .as("numeric cell in column " + ActivityReportWriter.HEADERS[i]
                                        + " is a date, not a bare number")
                                .isTrue();
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------- the dates

    @Test
    void timestamps_areRealDateCellsSoExcelCanSubtractThem() throws Exception {
        // The entire justification for producing .xlsx instead of CSV. As text,
        // "=Completed - Raised" returns #VALUE! and the reader has to clean the
        // column by hand before doing anything with it.
        Long id = anitaComplaint("Two days of work");
        assign(id);
        complete(id);
        setComplaintRaisedAt(id, ist(2026, 3, 5, 10, 0, 0));
        setCompletedAt(id, ist(2026, 3, 7, 10, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            Row row = dataRows(wb).get(0);
            for (String header : List.of("Raised On", "Completed On")) {
                Cell c = cell(row, header);
                assertThat(c.getCellType()).as(header + " is numeric").isEqualTo(CellType.NUMERIC);
                assertThat(DateUtil.isCellDateFormatted(c))
                        .as(header + " is formatted as a date").isTrue();
            }
            // And they really do subtract to two days.
            double days = cell(row, "Completed On").getNumericCellValue()
                    - cell(row, "Raised On").getNumericCellValue();
            assertThat(days).isEqualTo(2.0, org.assertj.core.data.Offset.offset(0.0001));
        }
    }

    @Test
    void timestamps_areShownInTheReportZone() throws Exception {
        // 20:30 UTC on 15 March is 02:00 on 16 March in India. The sheet must
        // show the Indian wall-clock time, because that is the day the reader
        // filed it under.
        Long id = anitaComplaint("Raised at two in the morning, Indian time");
        setComplaintRaisedAt(id, OffsetDateTime.of(2026, 3, 15, 20, 30, 0, 0, ZoneOffset.UTC));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            LocalDateTime shown = cell(dataRows(wb).get(0), "Raised On").getLocalDateTimeCellValue();
            assertThat(shown).isEqualTo(LocalDateTime.of(2026, 3, 16, 2, 0, 0));
        }
    }

    // ------------------------------------------------------------ isolation

    @Test
    void report_coversEveryFlat_notJustOne() throws Exception {
        Long anitas = anitaComplaint("From flat A-101");
        setComplaintRaisedAt(anitas, ist(2026, 3, 3, 9, 0, 0));
        Long binas = raiseComplaint(binaId, "Bina Rao", binaFlat, Category.CARPENTER, "From flat B-202");
        setComplaintRaisedAt(binas, ist(2026, 3, 4, 9, 0, 0));

        try (XSSFWorkbook wb = open(downloadMarch())) {
            assertThat(columnValues(wb, "Flat")).containsExactly(anitaFlat, binaFlat);
        }
    }
}
