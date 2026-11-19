package com.psgitech.library;

import com.psgitech.library.exception.InvalidDateRangeException;
import com.psgitech.library.service.report.LibrarianReportService;
import com.psgitech.library.service.report.ReportFilter;
import com.psgitech.library.service.report.ReportResult;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.*;

public class LibrarianAll11ReportsTest {

    private final LibrarianReportService reportService = new LibrarianReportService();

    @Test
    public void testAll11ReportsGenerateSuccessfully() throws InvalidDateRangeException {
        String[] reportTypes = {
                "MONTHLY_BORROW_VS_RETURN",
                "TOP_10_BOOKS",
                "POPULAR_CATEGORIES",
                "DEPT_BORROWING",
                "MONTHLY_FINES",
                "INVENTORY_SPLIT",
                "OVERDUE_AGE_BUCKETS",
                "TOP_BORROWERS",
                "ACQUISITION_EXPENDITURE",
                "COPY_SHORTAGE_BOTTLENECKS",
                "ZERO_CIRCULATION_WARMERS"
        };

        assertEquals(11, reportTypes.length);

        for (String type : reportTypes) {
            ReportFilter filter = new ReportFilter();
            filter.setReportType(type);
            filter.setPreset("LAST_30_DAYS");

            ReportResult result = reportService.generateReport(filter);
            assertNotNull("Report failed for " + type, result);
            assertNotNull(result.getTitle());
            assertNotNull(result.getHeaders());
            assertFalse("Headers empty for " + type, result.getHeaders().isEmpty());
            assertNotNull(result.getRows());

            // Test CSV Export
            String csv = reportService.exportToCsv(result);
            assertNotNull(csv);
            // Verify UTF-8 BOM
            assertTrue("CSV missing UTF-8 BOM", csv.startsWith("\uFEFF"));
            // Verify Title in CSV header comments
            assertTrue(csv.contains(result.getTitle()));
        }
    }

    @Test(expected = InvalidDateRangeException.class)
    public void testInvalidCustomDateRangeThrowsException() throws InvalidDateRangeException {
        ReportFilter filter = new ReportFilter();
        filter.setPreset("CUSTOM");
        filter.setFromDate(LocalDate.now());
        filter.setToDate(LocalDate.now().minusDays(5)); // from > to

        reportService.generateReport(filter);
    }
}
