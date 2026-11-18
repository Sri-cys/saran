package com.psgitech.library.service.report;

import com.psgitech.library.exception.InvalidDateRangeException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

public class ReportFilter {
    private String reportType;
    private String preset; // TODAY, LAST_7_DAYS, LAST_30_DAYS, THIS_MONTH, THIS_YEAR, CUSTOM
    private LocalDate fromDate;
    private LocalDate toDate;
    private String department;

    public ReportFilter() {
        this.preset = "LAST_30_DAYS";
    }

    public ReportFilter(String reportType, String preset, LocalDate fromDate, LocalDate toDate, String department) {
        this.reportType = reportType;
        this.preset = (preset != null && !preset.isEmpty()) ? preset.toUpperCase() : "LAST_30_DAYS";
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.department = department;
    }

    public void resolveDates() throws InvalidDateRangeException {
        LocalDate today = LocalDate.now();
        if ("TODAY".equalsIgnoreCase(preset)) {
            this.fromDate = today;
            this.toDate = today;
        } else if ("LAST_7_DAYS".equalsIgnoreCase(preset)) {
            this.fromDate = today.minusDays(6);
            this.toDate = today;
        } else if ("LAST_30_DAYS".equalsIgnoreCase(preset)) {
            this.fromDate = today.minusDays(29);
            this.toDate = today;
        } else if ("THIS_MONTH".equalsIgnoreCase(preset)) {
            this.fromDate = today.with(TemporalAdjusters.firstDayOfMonth());
            this.toDate = today;
        } else if ("THIS_YEAR".equalsIgnoreCase(preset)) {
            this.fromDate = today.with(TemporalAdjusters.firstDayOfYear());
            this.toDate = today;
        } else {
            // CUSTOM
            if (this.fromDate == null || this.toDate == null) {
                throw new InvalidDateRangeException("Custom date range requires both 'from' and 'to' dates.");
            }
            if (this.fromDate.isAfter(this.toDate)) {
                throw new InvalidDateRangeException("Start date (" + this.fromDate + ") cannot be after end date (" + this.toDate + ").");
            }
            long days = ChronoUnit.DAYS.between(this.fromDate, this.toDate);
            if (days > 366) {
                throw new InvalidDateRangeException("Date range cannot exceed 366 days (selected range is " + days + " days).");
            }
        }
    }

    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getPreset() { return preset; }
    public void setPreset(String preset) { this.preset = preset; }

    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }

    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
}
