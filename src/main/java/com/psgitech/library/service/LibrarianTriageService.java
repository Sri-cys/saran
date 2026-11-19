package com.psgitech.library.service;

import com.psgitech.library.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service providing real-time Exception Triage counts and actionable drill-downs
 * across Queues, Transfers, Fines, and Inventory.
 */
public class LibrarianTriageService {
    private static final Logger LOGGER = Logger.getLogger(LibrarianTriageService.class.getName());

    public Map<String, Object> getTriageSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("expiringAllocationsCount", 0);
        summary.put("stalledTransfersCount", 0);
        summary.put("pendingLostDamagedCount", 0);
        summary.put("retireEligibleCopiesCount", 0);
        summary.put("criticalOverdueCount", 0);
        summary.put("totalExceptions", 0);

        try (Connection conn = DBConnection.getConnection()) {
            // 1. Allocations expiring in less than 2 hours
            String sqlExpiring = "SELECT COUNT(*) FROM allocations " +
                    "WHERE status = 'ALLOCATED' AND expires_at <= DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 2 HOUR)";
            try (PreparedStatement ps = conn.prepareStatement(sqlExpiring);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) summary.put("expiringAllocationsCount", rs.getInt(1));
            }

            // 2. Stalled transfers (>24h unverified OTP)
            String sqlStalled = "SELECT COUNT(*) FROM book_interchanges " +
                    "WHERE status IN ('REQUESTED', 'ACCEPTED') AND created_at <= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 24 HOUR)";
            try (PreparedStatement ps = conn.prepareStatement(sqlStalled);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) summary.put("stalledTransfersCount", rs.getInt(1));
            }

            // 3. Pending lost/damaged replacements
            String sqlCases = "SELECT COUNT(*) FROM loss_damage_cases " +
                    "WHERE status IN ('REPORTED', 'UNDER_REVIEW', 'PAYMENT_PENDING')";
            try (PreparedStatement ps = conn.prepareStatement(sqlCases);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) summary.put("pendingLostDamagedCount", rs.getInt(1));
            }

            // 4. Retire-eligible worn copies
            String sqlRetire = "SELECT COUNT(*) FROM book_copies " +
                    "WHERE status = 'ARCHIVED' OR copy_id IN (SELECT copy_id FROM loss_damage_cases WHERE status = 'REPORTED')";
            try (PreparedStatement ps = conn.prepareStatement(sqlRetire);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) summary.put("retireEligibleCopiesCount", rs.getInt(1));
            }

            // 5. 15+ days overdue loans
            String sqlOverdue = "SELECT COUNT(*) FROM borrow_transactions " +
                    "WHERE status = 'ACTIVE' AND due_date <= DATE_SUB(CURRENT_DATE, INTERVAL 15 DAY)";
            try (PreparedStatement ps = conn.prepareStatement(sqlOverdue);
                 ResultSet rs = ps.executeQuery()) {
                summary.put("criticalOverdueCount", rs.next() ? rs.getInt(1) : 0);
            }

            int total = ((Number) summary.get("expiringAllocationsCount")).intValue()
                    + ((Number) summary.get("stalledTransfersCount")).intValue()
                    + ((Number) summary.get("pendingLostDamagedCount")).intValue()
                    + ((Number) summary.get("retireEligibleCopiesCount")).intValue()
                    + ((Number) summary.get("criticalOverdueCount")).intValue();
            summary.put("totalExceptions", total);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to compute triage summary", e);
        }

        return summary;
    }

    public List<Map<String, Object>> getDrilldown(String section) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (section == null) return list;

        try (Connection conn = DBConnection.getConnection()) {
            switch (section.toLowerCase()) {
                case "allocations":
                case "expiring_allocations": {
                    String sql = "SELECT a.allocation_id, a.reservation_id, a.copy_id, bc.copy_code, b.title AS book_title, " +
                            "u.user_id AS student_id, u.name AS student_name, COALESCE(u.register_number, u.email) AS reg_no, " +
                            "a.expires_at AS pickup_deadline, TIMESTAMPDIFF(MINUTE, CURRENT_TIMESTAMP, a.expires_at) AS minutes_remaining " +
                            "FROM allocations a " +
                            "JOIN book_copies bc ON a.copy_id = bc.copy_id " +
                            "JOIN books b ON bc.book_id = b.book_id " +
                            "JOIN reservations r ON a.reservation_id = r.reservation_id " +
                            "JOIN users u ON r.student_id = u.user_id " +
                            "WHERE a.status = 'ALLOCATED' AND a.expires_at <= DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 2 HOUR) " +
                            "ORDER BY a.expires_at ASC";
                    try (PreparedStatement ps = conn.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> m = new HashMap<>();
                            m.put("allocationId", rs.getInt("allocation_id"));
                            m.put("reservationId", rs.getInt("reservation_id"));
                            m.put("copyCode", rs.getString("copy_code"));
                            m.put("bookTitle", rs.getString("book_title"));
                            m.put("studentName", rs.getString("student_name"));
                            m.put("registerNumber", rs.getString("reg_no"));
                            m.put("pickupDeadline", rs.getString("pickup_deadline"));
                            m.put("minutesRemaining", rs.getInt("minutes_remaining"));
                            list.add(m);
                        }
                    }
                    break;
                }
                case "transfers":
                case "stalled_transfers": {
                    // Note: OTP codes and hashes are strictly excluded for security
                    String sql = "SELECT bi.interchange_id, b.title AS book_title, " +
                            "u1.name AS sender_name, COALESCE(u1.register_number, u1.email) AS sender_reg, " +
                            "u2.name AS receiver_name, COALESCE(u2.register_number, u2.email) AS receiver_reg, " +
                            "bi.status, bi.created_at, TIMESTAMPDIFF(HOUR, bi.created_at, CURRENT_TIMESTAMP) AS hours_stalled " +
                            "FROM book_interchanges bi " +
                            "JOIN books b ON bi.book_id = b.book_id " +
                            "JOIN users u1 ON bi.from_student_id = u1.user_id " +
                            "JOIN users u2 ON bi.to_student_id = u2.user_id " +
                            "WHERE bi.status IN ('REQUESTED', 'ACCEPTED') AND bi.created_at <= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 24 HOUR) " +
                            "ORDER BY bi.created_at ASC";
                    try (PreparedStatement ps = conn.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> m = new HashMap<>();
                            m.put("interchangeId", rs.getInt("interchange_id"));
                            m.put("bookTitle", rs.getString("book_title"));
                            m.put("senderName", rs.getString("sender_name"));
                            m.put("senderReg", rs.getString("sender_reg"));
                            m.put("receiverName", rs.getString("receiver_name"));
                            m.put("receiverReg", rs.getString("receiver_reg"));
                            m.put("status", rs.getString("status"));
                            m.put("createdAt", rs.getString("created_at"));
                            m.put("hoursStalled", rs.getInt("hours_stalled"));
                            m.put("otpStatus", "Not Verified (Stalled)");
                            list.add(m);
                        }
                    }
                    break;
                }
                case "cases":
                case "lost_damaged": {
                    String sql = "SELECT c.case_id, b.title AS book_title, bc.copy_code, " +
                            "u.name AS student_name, COALESCE(u.register_number, u.email) AS reg_no, " +
                            "c.case_type, c.status, c.created_at " +
                            "FROM loss_damage_cases c " +
                            "JOIN book_copies bc ON c.copy_id = bc.copy_id " +
                            "JOIN books b ON bc.book_id = b.book_id " +
                            "JOIN users u ON c.student_id = u.user_id " +
                            "WHERE c.status IN ('REPORTED', 'UNDER_REVIEW', 'PAYMENT_PENDING') " +
                            "ORDER BY c.created_at ASC";
                    try (PreparedStatement ps = conn.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> m = new HashMap<>();
                            m.put("caseId", rs.getInt("case_id"));
                            m.put("bookTitle", rs.getString("book_title"));
                            m.put("copyCode", rs.getString("copy_code"));
                            m.put("studentName", rs.getString("student_name"));
                            m.put("registerNumber", rs.getString("reg_no"));
                            m.put("caseType", rs.getString("case_type"));
                            m.put("status", rs.getString("status"));
                            m.put("createdAt", rs.getString("created_at"));
                            list.add(m);
                        }
                    }
                    break;
                }
                case "worn_copies":
                case "retire_eligible": {
                    String sql = "SELECT bc.copy_id, bc.copy_code, b.title AS book_title, b.isbn, " +
                            "bc.status, " +
                            "COUNT(bt.transaction_id) AS total_circulation_count " +
                            "FROM book_copies bc " +
                            "JOIN books b ON bc.book_id = b.book_id " +
                            "LEFT JOIN borrow_transactions bt ON bc.copy_id = bt.copy_id " +
                            "WHERE bc.status = 'ARCHIVED' OR bc.copy_id IN (SELECT copy_id FROM loss_damage_cases WHERE status = 'REPORTED') " +
                            "GROUP BY bc.copy_id, bc.copy_code, b.title, b.isbn, bc.status " +
                            "ORDER BY total_circulation_count DESC LIMIT 50";
                    try (PreparedStatement ps = conn.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> m = new HashMap<>();
                            m.put("copyId", rs.getInt("copy_id"));
                            m.put("copyCode", rs.getString("copy_code"));
                            m.put("bookTitle", rs.getString("book_title"));
                            m.put("isbn", rs.getString("isbn"));
                            m.put("status", rs.getString("status"));
                            m.put("circulationCount", rs.getInt("total_circulation_count"));
                            list.add(m);
                        }
                    }
                    break;
                }
                case "overdue":
                case "critical_overdue": {
                    String sql = "SELECT bt.transaction_id, b.title AS book_title, bc.copy_code, " +
                            "u.user_id AS student_id, u.name AS student_name, COALESCE(u.register_number, u.email) AS reg_no, " +
                            "bt.due_date, DATEDIFF(CURRENT_DATE, bt.due_date) AS days_overdue " +
                            "FROM borrow_transactions bt " +
                            "JOIN book_copies bc ON bt.copy_id = bc.copy_id " +
                            "JOIN books b ON bc.book_id = b.book_id " +
                            "JOIN users u ON bt.student_id = u.user_id " +
                            "WHERE bt.status = 'ACTIVE' AND bt.due_date <= DATE_SUB(CURRENT_DATE, INTERVAL 15 DAY) " +
                            "ORDER BY bt.due_date ASC";
                    try (PreparedStatement ps = conn.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String, Object> m = new HashMap<>();
                            m.put("transactionId", rs.getInt("transaction_id"));
                            m.put("bookTitle", rs.getString("book_title"));
                            m.put("copyCode", rs.getString("copy_code"));
                            m.put("studentId", rs.getInt("student_id"));
                            m.put("studentName", rs.getString("student_name"));
                            m.put("registerNumber", rs.getString("reg_no"));
                            m.put("dueDate", rs.getString("due_date"));
                            m.put("daysOverdue", rs.getInt("days_overdue"));
                            list.add(m);
                        }
                    }
                    break;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to retrieve triage drilldown for " + section, e);
        }

        return list;
    }
}
