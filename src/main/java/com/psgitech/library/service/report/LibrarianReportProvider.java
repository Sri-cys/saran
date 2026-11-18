package com.psgitech.library.service.report;

import com.psgitech.library.exception.InvalidDateRangeException;

public interface LibrarianReportProvider {
    ReportResult generateReport(ReportFilter filter) throws InvalidDateRangeException;
}
