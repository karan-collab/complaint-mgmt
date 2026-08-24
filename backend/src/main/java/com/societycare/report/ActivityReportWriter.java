package com.societycare.report;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Turns report rows into a real .xlsx workbook.
 *
 * Why a workbook and not a CSV: the whole point of this export is that somebody
 * runs formulas over it. A CSV carries no types, so every date arrives as text
 * and Excel re-parses it against the reader's locale - which is how "01-08-2026"
 * becomes 8 January on one laptop and 1 August on another, and why
 * {@code =Completed - Raised} returns #VALUE! rather than a number of days.
 * Here the timestamps are written as genuine date cells, so they subtract.
 *
 * SXSSF (streaming) keeps a bounded number of rows in memory and spills the
 * rest to a temp file. The data is small today, but production is a 1 GB box
 * whose API is deliberately tuned to 171 MB, and a report is exactly the kind
 * of endpoint that gets pointed at "all of last year" one day.
 */
@Component
public class ActivityReportWriter {

    /** Column order of the sheet. Asserted by ActivityReportWriterTest. */
    static final String[] HEADERS = {
            "Type",
            "Flat",
            "Resident",
            "Category",
            "Complaint",
            "Suggestion",
            "Status",
            "Assigned To",
            "Raised On",
            "Assigned On",
            "Completed On",
    };

    /** Characters of width per column, in the same order as HEADERS. */
    private static final int[] COLUMN_WIDTHS = {
            12, 10, 22, 14, 52, 52, 20, 22, 20, 20, 20,
    };

    /** How many rows SXSSF holds before flushing to disk. */
    private static final int ROW_ACCESS_WINDOW = 200;

    private static final String DATE_FORMAT = "dd-mmm-yyyy hh:mm";

    /**
     * @param rows  already ordered; this writer does not sort
     * @param zone  the zone the timestamps are rendered in - a spreadsheet cell
     *              has no offset of its own, so the instant has to be resolved
     *              to a wall-clock time before it is written
     */
    public byte[] write(List<ReportRow> rows, LocalDate from, LocalDate to, ZoneId zone) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW);
        try {
            Sheet sheet = workbook.createSheet("Activity");

            CellStyle headerStyle = headerStyle(workbook);
            CellStyle dateStyle = dateStyle(workbook);

            writeHeader(sheet, headerStyle);
            int rowNum = 1;
            for (ReportRow row : rows) {
                writeRow(sheet.createRow(rowNum++), row, dateStyle, zone);
            }

            // The header stays put while somebody scrolls a year of tickets.
            sheet.createFreezePane(0, 1);
            // Explicit widths rather than autoSizeColumn: SXSSF has already
            // flushed most rows out of memory by the time sizing would run, so
            // auto-sizing would measure only the last window of rows.
            for (int i = 0; i < COLUMN_WIDTHS.length; i++) {
                sheet.setColumnWidth(i, COLUMN_WIDTHS[i] * 256);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the activity report", e);
        } finally {
            // Deletes the temp files SXSSF spilled to; without this they sit in
            // the container's tmpdir until it restarts.
            workbook.dispose();
            closeQuietly(workbook);
        }
    }

    /** The name the browser saves the file as. */
    public String fileName(LocalDate from, LocalDate to) {
        return "societycare-report-" + from + "-to-" + to + ".xlsx";
    }

    private void writeHeader(Sheet sheet, CellStyle style) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(style);
        }
    }

    private void writeRow(Row row, ReportRow data, CellStyle dateStyle, ZoneId zone) {
        text(row, 0, data.getType());
        text(row, 1, data.getFlatNo());
        text(row, 2, data.getResidentName());
        text(row, 3, data.getCategory());
        text(row, 4, data.getComplaint());
        text(row, 5, data.getSuggestion());
        text(row, 6, data.getStatus());
        text(row, 7, data.getAssignedTo());
        date(row, 8, data.getCreatedAt(), dateStyle, zone);
        date(row, 9, data.getAssignedAt(), dateStyle, zone);
        date(row, 10, data.getCompletedAt(), dateStyle, zone);
    }

    /**
     * A null leaves the cell absent rather than writing an empty string. An
     * absent cell is genuinely blank to Excel, so COUNTA and COUNTBLANK agree
     * with what the reader sees; an empty string looks blank but counts.
     */
    private void text(Row row, int column, String value) {
        if (value == null || value.isEmpty()) return;
        row.createCell(column).setCellValue(value);
    }

    private void date(Row row, int column, OffsetDateTime value, CellStyle style, ZoneId zone) {
        if (value == null) return;
        Cell cell = row.createCell(column);
        cell.setCellValue(value.atZoneSameInstant(zone).toLocalDateTime());
        cell.setCellStyle(style);
    }

    private CellStyle headerStyle(SXSSFWorkbook workbook) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(bold);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle dateStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat(DATE_FORMAT));
        return style;
    }

    private void closeQuietly(SXSSFWorkbook workbook) {
        try {
            workbook.close();
        } catch (IOException ignored) {
            // The bytes are already produced; a failure to close the temp
            // handle must not turn a good report into a 500.
        }
    }
}
