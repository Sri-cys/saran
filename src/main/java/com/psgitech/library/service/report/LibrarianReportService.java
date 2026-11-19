package com.psgitech.library.service.report;

import com.psgitech.library.exception.InvalidDateRangeException;
import com.psgitech.library.util.DBConnection;
import com.psgitech.library.util.Rfc4180Parser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LibrarianReportService implements LibrarianReportProvider {
    private static final Logger LOGGER = Logger.getLogger(LibrarianReportService.class.getName());

    @Override
    public ReportResult generateReport(ReportFilter filter) throws InvalidDateRangeException {
        if (filter == null) {
            filter = new ReportFilter();
        }
        filter.resolveDates();

        String type = filter.getReportType() != null ? filter.getReportType().toUpperCase() : "MONTHLY_BORROW_VS_RETURN";

        switch (type) {
            case "TOP_10_BOOKS":
                return generateTop10Books(filter);
            case "POPULAR_CATEGORIES":
                return generatePopularCategories(filter);
            case "DEPT_BORROWING":
                return generateDeptBorrowing(filter);
            case "MONTHLY_FINES":
                return generateMonthlyFines(filter);
            case "INVENTORY_SPLIT":
                return generateInventorySplit(filter);
            case "OVERDUE_AGE_BUCKETS":
                return generateOverdueAgeBuckets(filter);
            case "TOP_BORROWERS":
                return generateTopBorrowers(filter);
            case "ACQUISITION_EXPENDITURE":
                return generateAcquisitionExpenditure(filter);
            case "COPY_SHORTAGE_BOTTLENECKS":
            case "COPY_SHORTAGE":
                return generateCopyShortageBottlenecks(filter);
            case "ZERO_CIRCULATION_WARMERS":
            case "ZERO_CIRCULATION":
            case "SHELF_WARMERS":
                return generateZeroCirculationWarmers(filter);
            case "MONTHLY_BORROW_VS_RETURN":
            default:
                return generateMonthlyBorrowVsReturn(filter);
        }
    }

    public String exportToCsv(ReportResult report) {
        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM
        sb.append('\uFEFF');

        // Title and Date Range header comments
        sb.append("# ").append(report.getTitle()).append("\r\n");
        sb.append("# Period: ").append(report.getFromDate()).append(" to ").append(report.getToDate()).append("\r\n\r\n");

        // Table Headers
        sb.append(Rfc4180Parser.toCsvLine(report.getHeaders()));

        // Table Rows
        for (List<Object> row : report.getRows()) {
            sb.append(Rfc4180Parser.toCsvLine(row));
        }

        return sb.toString();
    }

    private ReportResult generateMonthlyBorrowVsReturn(ReportFilter filter) {
        ReportResult res = new ReportResult("MONTHLY_BORROW_VS_RETURN", "Monthly Borrowing vs Returns",
                filter.getFromDate().toString(), filter.getToDate().toString());
        res.setHeaders(List.of("Month", "Total Borrowed", "Total Returned", "Net Circulation"));

        String sql = "SELECT m.month_key, " +
                     "COALESCE(b.borrow_count, 0) AS borrow_count, " +
                     "COALESCE(r.return_count, 0) AS return_count " +
                     "FROM (" +
                     "    SELECT DATE_FORMAT(issue_date, '%Y-%m') AS month_key FROM borrow_transactions WHERE issue_date BETWEEN ? AND ? " +
                     "    UNION " +
                     "    SELECT DATE_FORMAT(return_date, '%Y-%m') AS month_key FROM borrow_transactions WHERE return_date IS NOT NULL AND return_date BETWEEN ? AND ? " +
                     ") m " +
                     "LEFT JOIN (" +
                     "    SELECT DATE_FORMAT(issue_date, '%Y-%m') AS month_key, COUNT(*) AS borrow_count " +
                     "    FROM borrow_transactions WHERE issue_date BETWEEN ? AND ? GROUP BY month_key " +
                     ") b ON m.month_key = b.month_key " +
                     "LEFT JOIN (" +
                     "    SELECT DATE_FORMAT(return_date, '%Y-%m') AS month_key, COUNT(*) AS return_count " +
                     "    FROM borrow_transactions WHERE return_date IS NOT NULL AND return_date BETWEEN ? AND ? GROUP BY month_key " +
                     ") r ON m.month_key = r.month_key " +
                     "ORDER BY m.month_key ASC";

        List<String> labels = new ArrayList<>();
        List<Number> borrowData = new ArrayList<>();
        List<Number> returnData = new ArrayList<>();

        int totalB = 0;
        int totalR = 0;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            Date from = Date.valueOf(filter.getFromDate());
            Date to = Date.valueOf(filter.getToDate());
            ps.setDate(1, from); ps.setDate(2, to);
            ps.setDate(3, from); ps.setDate(4, to);
            ps.setDate(5, from); ps.setDate(6, to);
            ps.setDate(7, from); ps.setDate(8, to);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String month = rs.getString("month_key");
                    int bCount = rs.getInt("borrow_count");
                    int rCount = rs.getInt("return_count");
                    int net = bCount - rCount;

                    res.getRows().add(List.of(month, bCount, rCount, net));
                    labels.add(month);
                    borrowData.add(bCount);
                    returnData.add(rCount);

                    totalB += bCount;
                    totalR += rCount;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Report generation failed: " + res.getReportType(), e);
        }

        res.getChartData().put("labels", labels);
        res.getChartData().put("datasets", List.of(
                Map.of("label", "Borrowed", "data", borrowData, "color", "#0A2540"),
                Map.of("label", "Returned", "data", returnData, "color", "#00D4B2")
        ));
        res.getSummary().put("totalBorrowed", totalB);
        res.getSummary().put("totalReturned", totalR);
        res.getSummary().put("netCirculation", totalB - totalR);
        return res;
    }

    private ReportResult generateTop10Books(ReportFilter filter) {
        ReportResult res = new ReportResult("TOP_10_BOOKS", "Top 10 Most Borrowed Books",
                filter.getFromDate().toString(), filter.getToDate().toString());
        res.setHeaders(List.of("Rank", "Book Title", "Author", "Category", "Times Borrowed", "Available Copies", "Total Copies"));

        String sql = "SELECT b.book_id, b.title, b.author, b.category, COUNT(bt.transaction_id) AS borrow_count, " +
                     "COUNT(DISTINCT CASE WHEN bc.status = 'AVAILABLE' THEN bc.copy_id END) AS available_copies, " +
                     "COUNT(DISTINCT bc.copy_id) AS total_copies " +
                     "FROM books b " +
                     "JOIN borrow_transactions bt ON b.book_id = bt.book_id " +
                     "LEFT JOIN book_copies bc ON b.book_id = bc.book_id " +
                     "WHERE bt.issue_date BETWEEN ? AND ? " +
                     "GROUP BY b.book_id, b.title, b.author, b.category " +
                     "ORDER BY borrow_count DESC, b.title ASC LIMIT 10";

        List<String> labels = new ArrayList<>();
        List<Number> borrowCounts = new ArrayList<>();
        int rank = 1;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(filter.getFromDate()));
            ps.setDate(2, Date.valueOf(filter.getToDate()));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String title = rs.getString("title");
                    String author = rs.getString("author");
                    String cat = rs.getString("category");
                    int bCount = rs.getInt("borrow_count");
                    int avail = rs.getInt("available_copies");
                    int total = rs.getInt("total_copies");

                    res.getRows().add(List.of(rank, title, author, cat, bCount, avail, total));
                    labels.add(title.length() > 20 ? title.substring(0, 18) + ".." : title);
                    borrowCounts.add(bCount);
                    rank++;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Report generation failed: " + res.getReportType(), e);
        }

        res.getChartData().put("labels", labels);
        res.getChartData().put("datasets", List.of(Map.of("label", "Times Borrowed", "data", borrowCounts, "color", "#0A2540")));
        return res;
    }

    private ReportResult generatePopularCategories(ReportFilter filter) {
        ReportResult res = new ReportResult("POPULAR_CATEGORIES", "Popular Categories Breakdown",
                filter.getFromDate().toString(), filter.getToDate().toString());
        res.setHeaders(List.of("Category", "Borrow Count", "Share (%)"));

        String sql = "SELECT b.category, COUNT(bt.transaction_id) AS borrow_count " +
                     "FROM books b " +
                     "JOIN borrow_transactions bt ON b.book_id = bt.book_id " +
                     "WHERE bt.issue_date BETWEEN ? AND ? " +
                     "GROUP BY b.category ORDER BY borrow_count DESC";

        List<String> labels = new ArrayList<>();
        List<Number> counts = new ArrayList<>();
        int totalBorrows = 0;
        List<Map<String, Object>> temp = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(filter.getFromDate()));
            ps.setDate(2, Date.valueOf(filter.getToDate()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String cat = rs.getString("category") != null ? rs.getString("category") : "General";
                    int c = rs.getInt("borrow_count");
                    totalBorrows += c;
                    temp.add(Map.of("cat", cat, "count", c));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Report generation failed: " + res.getReportType(), e);
        }

        for (Map<String, Object> m : temp) {
            String cat = (String) m.get("cat");
            int c = (Integer) m.get("count");
            double pct = totalBorrows > 0 ? (c * 100.0) / totalBorrows : 0.0;
            res.getRows().add(List.of(cat, c, String.format("%.1f%%", pct)));
            labels.add(cat);
            counts.add(c);
        }

        res.getChartData().put("labels", labels);
        res.getChartData().put("datasets", List.of(Map.of("label", "Borrow Count", "data", counts, "color", "#F5A623")));
        res.getSummary().put("totalBorrowings", totalBorrows);
        return res;
    }

    private ReportResult generateDeptBorrowing(ReportFilter filter) {
        ReportResult res = new ReportResult("DEPT_BORROWING", "Department-Wise Borrowing Analysis",
                filter.getFromDate().toString(), filter.getToDate().toString());
        res.setHeaders(List.of("Department", "Total Borrowings", "Active Students"));

        String sql = "SELECT COALESCE(NULLIF(u.department, ''), 'General') AS dept_name, " +
                     "COUNT(bt.transaction_id) AS total_borrows, " +
                     "COUNT(DISTINCT bt.student_id) AS active_students " +
                     "FROM borrow_transactions bt " +
                     "JOIN users u ON bt.student_id = u.user_id " +
                     "WHERE bt.issue_date BETWEEN ? AND ? " +
                     "GROUP BY dept_name ORDER BY total_borrows DESC";

        List<String> labels = new ArrayList<>();
        List<Number> borrows = new ArrayList<>();
        List<Number> students = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(filter.getFromDate()));
            ps.setDate(2, Date.valueOf(filter.getToDate()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String dept = rs.getString("dept_name");
                    int b = rs.getInt("total_borrows");
                    int s = rs.getInt("active_students");
                    res.getRows().add(List.of(dept, b, s));
                    labels.add(dept);
                    borrows.add(b);
                    students.add(s);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Report generation failed: " + res.getReportType(), e);
        }

        res.getChartData().put("labels", labels);
        res.getChartData().put("datasets", List.of(
                Map.of("label", "Borrowings", "data", borrows, "color", "#0A2540"),
                Map.of("label", "Active Students", "data", students, "color", "#4A90E2")
        ));
        return res;
    }

    private ReportResult generateMonthlyFines(ReportFilter filter) {
        ReportResult res = new ReportResult("MONTHLY_FINES", "Monthly Fine Collection Ledger",
                filter.getFromDate().toString(), filter.getToDate().toString());
        res.setHeaders(List.of("Month", "Assessed (Rs.)", "Collected (Rs.)", "Outstanding (Rs.)"));

        String sql = "SELECT DATE_FORMAT(created_at, '%Y-%m') AS month_key, " +
                     "COALESCE(SUM(original_amount), 0.00) AS total_assessed, " +
                     "COALESCE(SUM(paid_amount), 0.00) AS total_paid, " +
                     "COALESCE(SUM(outstanding_amount), 0.00) AS total_outstanding " +
                     "FROM fines WHERE created_at >= ? AND created_at <= ? " +
                     "GROUP BY month_key ORDER BY month_key ASC";

        List<String> labels = new ArrayList<>();
        List<Number> assessedList = new ArrayList<>();
        List<Number> paidList = new ArrayList<>();

        BigDecimal sumAssessed = BigDecimal.ZERO;
        BigDecimal sumPaid = BigDecimal.ZERO;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(filter.getFromDate().atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(filter.getToDate().atTime(23, 59, 59)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String month = rs.getString("month_key");
                    BigDecimal assessed = rs.getBigDecimal("total_assessed");
                    BigDecimal paid = rs.getBigDecimal("total_paid");
                    BigDecimal outstanding = rs.getBigDecimal("total_outstanding");

                    res.getRows().add(List.of(month, assessed, paid, outstanding));
                    labels.add(month);
                    assessedList.add(assessed);
                    paidList.add(paid);

                    sumAssessed = sumAssessed.add(assessed);
                    sumPaid = sumPaid.add(paid);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Report generation failed: " + res.getReportType(), e);
        }

        res.getChartData().put("labels", labels);
        res.getChartData().put("datasets", List.of(
                Map.of("label", "Assessed", "data", assessedList, "color", "#E02020"),
                Map.of("label", "Collected", "data", paidList, "color", "#00D4B2")
        ));
        res.getSummary().put("totalAssessed", sumAssessed);
        res.getSummary().put("totalCollected", sumPaid);
        res.getSummary().put("totalOutstanding", sumAssessed.subtract(sumPaid));
        return res;
    }

    private ReportResult generateInventorySplit(ReportFilter filter) {
        ReportResult res = new ReportResult("INVENTORY_SPLIT", "Physical Inventory Status Split",
                filter.getFromDate().toString(), filter.getToDate().toString());
        res.setHeaders(List.of("Status", "Physical Copies", "Share (%)"));

        String sql = "SELECT status, COUNT(*) AS copy_count FROM book_copies GROUP BY status ORDER BY copy_count DESC";

        List<String> labels = new ArrayList<>();
        List<Number> counts = new ArrayList<>();
        int totalCopies = 0;
        List<Map<String, Object>> temp = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String st = rs.getString("status");
                int c = rs.getInt("copy_count");
                totalCopies += c;
                temp.add(Map.of("status", st, "count", c));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Report generation failed: " + res.getReportType(), e);
        }

        for (Map<String, Object> m : temp) {
            String st = (String) m.get("status");
            int c = (Integer) m.get("count");
            double pct = totalCopies > 0 ? (c * 100.0) / totalCopies : 0.0;
            res.getRows().add(List.of(st, c, String.format("%.1f%%", pct)));
            labels.add(st);
            counts.add(c);
        }

        res.getChartData().put("labels", labels);
        res.getChartData().put("datasets", List.of(Map.of("label", "Physical Copies", "data", counts, "color", "#0A2540")));
        res.getSummary().put("totalPhysicalCopies", totalCopies);
        return res;
    }

    private ReportResult generateOverdueAgeBuckets(ReportFilter filter) {
        ReportResult res = new ReportResult("OVERDUE_AGE_BUCKETS", "Overdue Age Analysis Buckets",
                filter.getFromDate().toString(), filter.getToDate().toString());
        res.setHeaders(List.of("Overdue Age Bucket", "Overdue Loans Count", "Estimated Fine Accrual (Rs.)"));

        String sql = "SELECT " +
                     "SUM(CASE WHEN DATEDIFF(CURDATE(), due_date) BETWEEN 1 AND 7 THEN 1 ELSE 0 END) AS count_1_7, " +
                     "SUM(CASE WHEN DATEDIFF(CURDATE(), due_date) BETWEEN 8 AND 14 THEN 1 ELSE 0 END) AS count_8_14, " +
                     "SUM(CASE WHEN DATEDIFF(CURDATE(), due_date) >= 15 THEN 1 ELSE 0 END) AS count_15_plus, " +
                     "SUM(CASE WHEN DATEDIFF(CURDATE(), due_date) BETWEEN 1 AND 7 THEN DATEDIFF(CURDATE(), due_date) * 2 ELSE 0 END) AS fine_1_7, " +
                     "SUM(CASE WHEN DATEDIFF(CURDATE(), due_date) BETWEEN 8 AND 14 THEN DATEDIFF(CURDATE(), due_date) * 5 ELSE 0 END) AS fine_8_14, " +
                     "SUM(CASE WHEN DATEDIFF(CURDATE(), due_date) >= 15 THEN DATEDIFF(CURDATE(), due_date) * 10 ELSE 0 END) AS fine_15_plus " +
                     "FROM borrow_transactions WHERE status = 'ACTIVE' AND due_date < CURDATE()";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int c1 = rs.getInt("count_1_7");
                int c2 = rs.getInt("count_8_14");
                int c3 = rs.getInt("count_15_plus");
                BigDecimal f1 = rs.getBigDecimal("fine_1_7");
                BigDecimal f2 = rs.getBigDecimal("fine_8_14");
                BigDecimal f3 = rs.getBigDecimal("fine_15_plus");

                res.getRows().add(List.of("1 to 7 Days Overdue", c1, f1 != null ? f1 : 0));
                res.getRows().add(List.of("8 to 14 Days Overdue", c2, f2 != null ? f2 : 0));
                res.getRows().add(List.of("15+ Days Overdue", c3, f3 != null ? f3 : 0));

                res.getChartData().put("labels", List.of("1-7 Days", "8-14 Days", "15+ Days"));
                res.getChartData().put("datasets", List.of(
                        Map.of("label", "Loans Count", "data", List.of(c1, c2, c3), "color", "#E02020")
                ));
                res.getSummary().put("totalOverdueLoans", c1 + c2 + c3);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Report generation failed: " + res.getReportType(), e);
        }
        return res;
    }

    private ReportResult generateTopBorrowers(ReportFilter filter) {
        ReportResult res = new ReportResult("TOP_BORROWERS", "Top Student Borrowers",
                filter.getFromDate().toString(), filter.getToDate().toString());
        res.setHeaders(List.of("Rank", "Student Name", "Register Number", "Department", "Total Borrows", "Active Loans"));

        String sql = "SELECT u.user_id, u.name, COALESCE(u.register_number, u.email) AS reg_no, " +
                     "COALESCE(NULLIF(u.department, ''), 'General') AS department, " +
                     "COUNT(bt.transaction_id) AS total_loans, " +
                     "SUM(CASE WHEN bt.status = 'ACTIVE' THEN 1 ELSE 0 END) AS active_loans " +
                     "FROM users u " +
                     "JOIN borrow_transactions bt ON u.user_id = bt.student_id " +
                     "WHERE bt.issue_date BETWEEN ? AND ? " +
                     "GROUP BY u.user_id, u.name, u.register_number, u.email, u.department " +
                     "ORDER BY total_loans DESC LIMIT 20";

        List<String> labels = new ArrayList<>();
        List<Number> loans = new ArrayList<>();
        int rank = 1;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(filter.getFromDate()));
            ps.setDate(2, Date.valueOf(filter.getToDate()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String regNo = rs.getString("reg_no");
                    String dept = rs.getString("department");
                    int total = rs.getInt("total_loans");
                    int active = rs.getInt("active_loans");

                    res.getRows().add(List.of(rank, name, regNo, dept, total, active));
                    labels.add(name);
                    loans.add(total);
                    rank++;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Report generation failed: " + res.getReportType(), e);
        }

        res.getChartData().put("labels", labels);
        res.getChartData().put("datasets", List.of(Map.of("label", "Total Loans", "data", loans, "color", "#0A2540")));
        return res;
    }

    private ReportResult generateAcquisitionExpenditure(ReportFilter filter) {
        ReportResult res = new ReportResult("ACQUISITION_EXPENDITURE", "Acquisition Expenditure Analysis",
                filter.getFromDate().toString(), filter.getToDate().toString());
        res.setHeaders(List.of("Period (Month)", "Invoices Count", "Total Items Purchased", "Expenditure (Rs.)"));

        String sql = "SELECT DATE_FORMAT(purchase_date, '%Y-%m') AS period_key, " +
                     "COUNT(*) AS invoice_count, " +
                     "COALESCE(SUM(quantity), 0) AS total_items, " +
                     "COALESCE(SUM(total_amount), 0.00) AS total_spend " +
                     "FROM book_purchases WHERE purchase_date BETWEEN ? AND ? " +
                     "GROUP BY period_key ORDER BY period_key ASC";

        List<String> labels = new ArrayList<>();
        List<Number> spend = new ArrayList<>();
        BigDecimal totalSpend = BigDecimal.ZERO;
        int totalItems = 0;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(filter.getFromDate()));
            ps.setDate(2, Date.valueOf(filter.getToDate()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String period = rs.getString("period_key");
                    int inv = rs.getInt("invoice_count");
                    int items = rs.getInt("total_items");
                    BigDecimal amt = rs.getBigDecimal("total_spend");

                    res.getRows().add(List.of(period, inv, items, amt));
                    labels.add(period);
                    spend.add(amt);

                    totalSpend = totalSpend.add(amt);
                    totalItems += items;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Report generation failed: " + res.getReportType(), e);
        }

        res.getChartData().put("labels", labels);
        res.getChartData().put("datasets", List.of(Map.of("label", "Expenditure (Rs.)", "data", spend, "color", "#F5A623")));
        res.getSummary().put("totalExpenditure", totalSpend);
        res.getSummary().put("totalItemsPurchased", totalItems);
        return res;
    }

    private ReportResult generateCopyShortageBottlenecks(ReportFilter filter) {
        ReportResult res = new ReportResult("COPY_SHORTAGE_BOTTLENECKS", "Copy-Shortage Bottlenecks",
                filter.getFromDate().toString(), filter.getToDate().toString());
        res.setHeaders(List.of("Rank", "Book ID", "Title", "Category", "Active Copies", "Waiting Queue", "Shortage Ratio", "Recommended Additions"));

        // Fetch shortage threshold ratio from config, default 2.0
        double shortageRatioThreshold = 2.0;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT setting_value FROM config_settings WHERE setting_key = 'copy_shortage_ratio'")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    shortageRatioThreshold = Double.parseDouble(rs.getString("setting_value").trim());
                }
            }
        } catch (Exception ignored) {}

        String sql = "SELECT b.book_id, b.title, b.category, " +
                     "COUNT(DISTINCT bc.copy_id) AS active_copies, " +
                     "COUNT(DISTINCT r.reservation_id) AS waiting_reservations " +
                     "FROM books b " +
                     "LEFT JOIN book_copies bc ON b.book_id = bc.book_id AND bc.status != 'RETIRED' " +
                     "LEFT JOIN reservations r ON b.book_id = r.book_id AND r.status = 'WAITING' " +
                     "GROUP BY b.book_id, b.title, b.category " +
                     "HAVING waiting_reservations > 0";

        List<String> labels = new ArrayList<>();
        List<Number> waiting = new ArrayList<>();
        List<Number> copies = new ArrayList<>();
        int rank = 1;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<Map<String, Object>> candidateList = new ArrayList<>();
            while (rs.next()) {
                int bookId = rs.getInt("book_id");
                String title = rs.getString("title");
                String category = rs.getString("category");
                int activeCopies = rs.getInt("active_copies");
                int waitingQueue = rs.getInt("waiting_reservations");

                // Guard divide by zero
                double ratio = activeCopies > 0 ? (double) waitingQueue / activeCopies : (double) waitingQueue * 2.0;
                if (ratio >= shortageRatioThreshold) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("bookId", bookId);
                    item.put("title", title);
                    item.put("category", category != null ? category : "General");
                    item.put("activeCopies", activeCopies);
                    item.put("waitingQueue", waitingQueue);
                    item.put("ratio", Math.round(ratio * 100.0) / 100.0);
                    int recommended = Math.max(1, waitingQueue - activeCopies);
                    item.put("recommended", recommended);
                    candidateList.add(item);
                }
            }

            // Sort by ratio descending
            candidateList.sort((a, b) -> Double.compare(((Number) b.get("ratio")).doubleValue(), ((Number) a.get("ratio")).doubleValue()));

            for (Map<String, Object> item : candidateList) {
                res.getRows().add(List.of(
                        rank,
                        item.get("bookId"),
                        item.get("title"),
                        item.get("category"),
                        item.get("activeCopies"),
                        item.get("waitingQueue"),
                        item.get("ratio"),
                        item.get("recommended")
                ));
                if (rank <= 10) {
                    labels.add((String) item.get("title"));
                    waiting.add((Number) item.get("waitingQueue"));
                    copies.add((Number) item.get("activeCopies"));
                }
                rank++;
            }

            res.getSummary().put("bottleneckTitlesCount", candidateList.size());
            res.getSummary().put("thresholdApplied", shortageRatioThreshold);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Report generation failed: " + res.getReportType(), e);
        }

        res.getChartData().put("labels", labels);
        res.getChartData().put("datasets", List.of(
                Map.of("label", "Waiting Queue", "data", waiting, "color", "#E02020"),
                Map.of("label", "Active Copies", "data", copies, "color", "#0A2540")
        ));
        return res;
    }

    private ReportResult generateZeroCirculationWarmers(ReportFilter filter) {
        ReportResult res = new ReportResult("ZERO_CIRCULATION_WARMERS", "Zero-Circulation Shelf Warmers (180+ Days)",
                filter.getFromDate().toString(), filter.getToDate().toString());
        res.setHeaders(List.of("Rank", "Copy ID", "Copy Code", "Book Title", "Category", "Location / Shelf", "Days Idle", "Action Recommendation"));

        String sql = "SELECT bc.copy_id, bc.copy_code, b.book_id, b.title, b.category, " +
                     "COALESCE(CONCAT(bl.floor, ' / ', bl.rack, ' / ', bl.shelf), 'Unassigned') AS location, " +
                     "DATEDIFF(CURRENT_DATE, COALESCE(MAX(bt.issue_date), bc.created_at)) AS days_idle " +
                     "FROM book_copies bc " +
                     "JOIN books b ON bc.book_id = b.book_id " +
                     "LEFT JOIN book_locations bl ON bc.copy_id = bl.copy_id " +
                     "LEFT JOIN borrow_transactions bt ON bc.copy_id = bt.copy_id " +
                     "WHERE bc.status != 'RETIRED' " +
                     "GROUP BY bc.copy_id, bc.copy_code, b.book_id, b.title, b.category, bl.floor, bl.rack, bl.shelf, bc.created_at " +
                     "HAVING days_idle >= 180 " +
                     "ORDER BY days_idle DESC LIMIT 100";

        List<String> labels = new ArrayList<>();
        List<Number> idleDays = new ArrayList<>();
        int rank = 1;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int copyId = rs.getInt("copy_id");
                String copyCode = rs.getString("copy_code");
                String title = rs.getString("title");
                String category = rs.getString("category") != null ? rs.getString("category") : "General";
                String location = rs.getString("location");
                int days = rs.getInt("days_idle");

                String rec = days >= 365 ? "Retire Review" : "Relocate to Prominent Shelf";

                res.getRows().add(List.of(rank, copyId, copyCode, title, category, location, days, rec));

                if (rank <= 10) {
                    labels.add(copyCode != null ? copyCode : ("Copy #" + copyId));
                    idleDays.add(days);
                }
                rank++;
            }

            res.getSummary().put("shelfWarmerCount", rank - 1);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Report generation failed: " + res.getReportType(), e);
        }

        res.getChartData().put("labels", labels);
        res.getChartData().put("datasets", List.of(
                Map.of("label", "Days Idle", "data", idleDays, "color", "#F5A623")
        ));
        return res;
    }
}
