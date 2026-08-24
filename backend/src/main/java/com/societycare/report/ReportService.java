package com.societycare.report;

import com.societycare.common.BadRequestException;
import com.societycare.complaint.Complaint;
import com.societycare.complaint.ComplaintRepository;
import com.societycare.status.Status;
import com.societycare.suggestion.Suggestion;
import com.societycare.suggestion.SuggestionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the management activity report: every complaint and every suggestion
 * raised inside a date range, as one flat table.
 *
 * <h3>Which date the range filters on</h3>
 * The date a thing was <em>raised</em>, never the date it was completed. So a
 * complaint raised in March and finished in April belongs to the March report
 * and carries an April completion date. That answers "what came in during this
 * period", which is the question a society committee actually asks.
 *
 * <h3>Why the zone is configured rather than taken from the JVM</h3>
 * "25 August" is not an instant, it is a day in somebody's calendar. The
 * production container runs in UTC and the people reading the report are in
 * India, so resolving the range against the JVM's default zone would quietly
 * shift every boundary by five and a half hours - a ticket raised at 2 a.m.
 * would land in the previous day's report. The zone is therefore explicit and
 * the same in dev, test and production.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private final ComplaintRepository complaintRepository;
    private final SuggestionRepository suggestionRepository;
    private final ZoneId zone;

    public ReportService(ComplaintRepository complaintRepository,
                         SuggestionRepository suggestionRepository,
                         @Value("${app.reports.time-zone:Asia/Kolkata}") String timeZone) {
        this.complaintRepository = complaintRepository;
        this.suggestionRepository = suggestionRepository;
        this.zone = ZoneId.of(timeZone);
    }

    public ZoneId getZone() {
        return zone;
    }

    /** First of the current month, in the report's zone. */
    public LocalDate defaultFrom() {
        return LocalDate.now(zone).withDayOfMonth(1);
    }

    /** Today, in the report's zone. */
    public LocalDate defaultTo() {
        return LocalDate.now(zone);
    }

    public List<ReportRow> buildRows(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BadRequestException(
                    "The 'from' date (" + from + ") is after the 'to' date (" + to + ")");
        }

        // Half-open interval: [start of the from-day, start of the day AFTER
        // the to-day). Written this way rather than as "<= 23:59:59" because
        // the columns hold sub-second precision - anything raised in the last
        // second of the day would fall through an inclusive upper bound.
        OffsetDateTime start = from.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime endExclusive = to.plusDays(1).atStartOfDay(zone).toOffsetDateTime();

        List<ReportRow> rows = new ArrayList<>();
        // Withdrawn complaints are left out, matching every screen in the app:
        // a complaint the resident pulled back is not work the society did.
        //
        // The rows are still in the database - soft delete kept them - so
        // reporting on withdrawals later means dropping this argument, not
        // recovering lost data.
        for (Complaint c : complaintRepository.findRaisedBetween(
                start, endExclusive, Status.DELETED)) {
            rows.add(ReportRow.complaint(
                    c.getComplaintId(),
                    c.getResident().getFlatNo(),
                    c.getResident().getResidentName(),
                    c.getCategory().getLabel(),
                    c.getDescription(),
                    displayStatus(c),
                    c.getProfessional() == null ? null : c.getProfessional().getName(),
                    c.getCreatedAt(),
                    c.getAssignedAt(),
                    c.getCompletedAt()));
        }
        for (Suggestion s : suggestionRepository.findRaisedBetween(start, endExclusive)) {
            rows.add(ReportRow.suggestion(
                    s.getSuggestionId(),
                    s.getResident().getFlatNo(),
                    s.getResident().getResidentName(),
                    s.getSuggestion(),
                    s.getCreatedAt()));
        }

        // Oldest first, so the sheet reads as a chronological log. Type and id
        // break ties: two rows can share a timestamp, and an arbitrary order
        // would make the output differ between runs on identical data.
        rows.sort(Comparator.comparing(ReportRow::getCreatedAt)
                .thenComparing(ReportRow::getType)
                .thenComparing(ReportRow::getId));
        return rows;
    }

    /**
     * The status as management sees it on screen, not the raw row name -
     * "Complete" reads as "Completed" everywhere in the UI.
     *
     * The withdrawn branch is unreachable while buildRows filters those rows
     * out, and is kept deliberately: the mapping stays complete, so putting
     * withdrawn complaints back is a change in one place rather than two.
     */
    private static String displayStatus(Complaint complaint) {
        int statusId = complaint.getStatus().getStatusId();
        switch (statusId) {
            case Status.ASSIGNMENT_PENDING:
                return "Assignment Pending";
            case Status.PENDING_WORK:
                return "Pending Work";
            case Status.COMPLETE:
                return "Completed";
            case Status.DELETED:
                return "Withdrawn";
            default:
                return complaint.getStatus().getStatusName();
        }
    }
}
