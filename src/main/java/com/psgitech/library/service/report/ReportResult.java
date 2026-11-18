package com.psgitech.library.service.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportResult {
    private String reportType;
    private String title;
    private String fromDate;
    private String toDate;
    private List<String> headers;
    private List<List<Object>> rows;
    private Map<String, Object> chartData;
    private Map<String, Object> summary;

    public ReportResult() {
        this.headers = new ArrayList<>();
        this.rows = new ArrayList<>();
        this.chartData = new HashMap<>();
        this.summary = new HashMap<>();
    }

    public ReportResult(String reportType, String title, String fromDate, String toDate) {
        this();
        this.reportType = reportType;
        this.title = title;
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getFromDate() { return fromDate; }
    public void setFromDate(String fromDate) { this.fromDate = fromDate; }

    public String getToDate() { return toDate; }
    public void setToDate(String toDate) { this.toDate = toDate; }

    public List<String> getHeaders() { return headers; }
    public void setHeaders(List<String> headers) { this.headers = headers; }

    public List<List<Object>> getRows() { return rows; }
    public void setRows(List<List<Object>> rows) { this.rows = rows; }

    public Map<String, Object> getChartData() { return chartData; }
    public void setChartData(Map<String, Object> chartData) { this.chartData = chartData; }

    public Map<String, Object> getSummary() { return summary; }
    public void setSummary(Map<String, Object> summary) { this.summary = summary; }
}
