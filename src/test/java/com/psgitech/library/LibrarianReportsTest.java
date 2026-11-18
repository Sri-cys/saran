package com.psgitech.library;

import com.psgitech.library.exception.InvalidDateRangeException;
import com.psgitech.library.service.report.LibrarianReportService;
import com.psgitech.library.service.report.ReportFilter;
import com.psgitech.library.service.report.ReportResult;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.*;

public class LibrarianReportsTest {

    private LibrarianReportService reportService;

    @Before
    public void setUp() {
        this.reportService = new LibrarianReportService();
    }

    @Test
    public void testPresetDateCalculation() throws InvalidDateRangeException {
        ReportFilter f1 = new ReportFilter("MONTHLY_BORROW_VS_RETURN", "TODAY", null, null, null);
        f1.resolveDates();
        assertEquals(LocalDate.now(), f1.getFromDate());
        assertEquals(LocalDate.now(), f1.getToDate());

        ReportFilter f2 = new ReportFilter("MONTHLY_BORROW_VS_RETURN", "LAST_7_DAYS", null, null, null);
        f2.resolveDates();
        assertEquals(LocalDate.now().minusDays(6), f2.getFromDate());
        assertEquals(LocalDate.now(), f2.getToDate());

        ReportFilter f3 = new ReportFilter("MONTHLY_BORROW_VS_RETURN", "LAST_30_DAYS", null, null, null);
        f3.resolveDates();
        assertEquals(LocalDate.now().minusDays(29), f3.getFromDate());
        assertEquals(LocalDate.now(), f3.getToDate());
    }

    @Test(expected = InvalidDateRangeException.class)
    public void testInvalidDateRangeExceptionWhenFromAfterTo() throws InvalidDateRangeException {
        ReportFilter filter = new ReportFilter("TOP_10_BOOKS", "CUSTOM", LocalDate.now(), LocalDate.now().minusDays(5), null);
        filter.resolveDates();
    }

    @Test(expected = InvalidDateRangeException.class)
    public void testInvalidDateRangeExceptionWhenSpanExceeds366Days() throws InvalidDateRangeException {
        ReportFilter filter = new ReportFilter("TOP_10_BOOKS", "CUSTOM", LocalDate.now().minusDays(400), LocalDate.now(), null);
        filter.resolveDates();
    }

    @Test(expected = InvalidDateRangeException.class)
    public void testInvalidDateRangeExceptionWhenDatesMissing() throws InvalidDateRangeException {
        ReportFilter filter = new ReportFilter("TOP_10_BOOKS", "CUSTOM", null, null, null);
        filter.resolveDates();
    }

    @Test
    public void testAllNineReportsGeneration() throws InvalidDateRangeException {
        String[] reportTypes = {
            "MONTHLY_BORROW_VS_RETURN",
            "TOP_10_BOOKS",
            "POPULAR_CATEGORIES",
            "DEPT_BORROWING",
            "MONTHLY_FINES",
            "INVENTORY_SPLIT",
            "OVERDUE_AGE_BUCKETS",
            "TOP_BORROWERS",
            "ACQUISITION_EXPENDITURE"
        };

        for (String type : reportTypes) {
            ReportFilter filter = new ReportFilter(type, "LAST_30_DAYS", null, null, "ALL");
            ReportResult result = reportService.generateReport(filter);

            assertNotNull("Result must not be null for " + type, result);
            assertEquals(type, result.getReportType());
            assertNotNull("Title required for " + type, result.getTitle());
            assertFalse("Headers must not be empty for " + type, result.getHeaders().isEmpty());
            assertNotNull("Rows must not be null for " + type, result.getRows());
            assertNotNull("ChartData must not be null for " + type, result.getChartData());
        }
    }

    @Test
    public void testFormulaSafeCsvExport() throws InvalidDateRangeException {
        ReportFilter filter = new ReportFilter("TOP_10_BOOKS", "LAST_30_DAYS", null, null, null);
        ReportResult result = reportService.generateReport(filter);

        String csv = reportService.exportToCsv(result);
        assertNotNull(csv);
        // Must start with UTF-8 BOM
        assertEquals('\uFEFF', csv.charAt(0));
        assertTrue(csv.contains("Top 10 Most Borrowed Books"));
        assertTrue(csv.contains("Rank,Book Title,Author"));
    }
}
