package com.psgitech.library.service;

import com.psgitech.library.dto.librarian.TrustStandingDTO;
import com.psgitech.library.dto.librarian.TrustStandingDTO.Tier;
import com.psgitech.library.repository.GoodwillExtensionRepository;
import com.psgitech.library.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Computes student trust standing tiers and evaluates eligibility for goodwill extensions.
 */
public class TrustStandingService {
    private static final Logger LOGGER = Logger.getLogger(TrustStandingService.class.getName());

    private final GoodwillExtensionRepository goodwillRepo;

    public TrustStandingService() {
        this.goodwillRepo = new GoodwillExtensionRepository();
    }

    public TrustStandingService(GoodwillExtensionRepository goodwillRepo) {
        this.goodwillRepo = goodwillRepo;
    }

    /**
     * Computes the real-time trust standing for a given student ID.
     */
    public TrustStandingDTO computeTrustStanding(int studentId) {
        TrustStandingDTO dto = new TrustStandingDTO();
        dto.setStudentId(studentId);

        try (Connection conn = DBConnection.getConnection()) {
            // 1. Basic student info
            String userSql = "SELECT name, register_number, email FROM users WHERE user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(userSql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        dto.setStudentName(rs.getString("name"));
                        String reg = rs.getString("register_number");
                        dto.setRegisterNumber(reg != null && !reg.isEmpty() ? reg : rs.getString("email"));
                    }
                }
            }

            // 2. Returns in past 12 months
            String returnSql = "SELECT COUNT(*) AS total_returns, " +
                    "SUM(CASE WHEN return_date <= due_date THEN 1 ELSE 0 END) AS on_time_returns " +
                    "FROM borrow_transactions " +
                    "WHERE student_id = ? AND status = 'RETURNED' AND return_date >= DATE_SUB(CURRENT_DATE, INTERVAL 12 MONTH)";
            try (PreparedStatement ps = conn.prepareStatement(returnSql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int total = rs.getInt("total_returns");
                        int onTime = rs.getInt("on_time_returns");
                        dto.setTotalReturns12m(total);
                        dto.setOnTimeReturns12m(onTime);
                        if (total > 0) {
                            dto.setOnTimeRatePercent(Math.round(((double) onTime / total * 100.0) * 10.0) / 10.0);
                        } else {
                            dto.setOnTimeRatePercent(100.0);
                        }
                    }
                }
            }

            // 3. Current active overdue loans
            String overdueSql = "SELECT COUNT(*) AS overdue_count FROM borrow_transactions " +
                    "WHERE student_id = ? AND status = 'ACTIVE' AND due_date < CURRENT_DATE";
            try (PreparedStatement ps = conn.prepareStatement(overdueSql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        dto.setActiveOverdueCount(rs.getInt("overdue_count"));
                    }
                }
            }

            // 4. Unpaid fines and oldest fine age
            String fineSql = "SELECT COALESCE(SUM(outstanding_amount), 0.00) AS unpaid_amt, " +
                    "COALESCE(MAX(DATEDIFF(CURRENT_DATE, created_at)), 0) AS max_days " +
                    "FROM fines WHERE student_id = ? AND status IN ('UNPAID', 'PARTIALLY_PAID')";
            try (PreparedStatement ps = conn.prepareStatement(fineSql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        dto.setUnpaidFinesAmount(rs.getBigDecimal("unpaid_amt"));
                        dto.setOldestUnpaidFineDays(rs.getInt("max_days"));
                    }
                }
            }

            // 5. Lost or damaged cases
            String caseSql = "SELECT COUNT(*) AS case_count FROM loss_damage_cases " +
                    "WHERE student_id = ? AND status IN ('REPORTED', 'UNDER_REVIEW', 'PAYMENT_PENDING')";
            try (PreparedStatement ps = conn.prepareStatement(caseSql)) {
                ps.setInt(1, studentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        dto.setLostOrDamagedCount(rs.getInt("case_count"));
                    }
                }
            }

            // 6. Check if student already utilized goodwill extension
            boolean used = goodwillRepo.hasUsedGoodwillExtension(studentId);
            dto.setGoodwillAlreadyUsed(used);

            // 7. Threshold configurations
            BigDecimal fineBlockThreshold = new BigDecimal("100.00");
            int minReturnsForExemplary = 5;
            try {
                String cfgSql = "SELECT setting_key, setting_value FROM config_settings " +
                        "WHERE setting_key IN ('trust_exemplary_min_returns', 'fine_block_threshold')";
                try (PreparedStatement ps = conn.prepareStatement(cfgSql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String key = rs.getString("setting_key");
                        String val = rs.getString("setting_value");
                        if ("trust_exemplary_min_returns".equals(key)) {
                            minReturnsForExemplary = Integer.parseInt(val.trim());
                        } else if ("fine_block_threshold".equals(key)) {
                            fineBlockThreshold = new BigDecimal(val.trim());
                        }
                    }
                }
            } catch (Exception ignored) {}

            // 8. Derive Tier & Human-readable Explanation
            if (dto.getActiveOverdueCount() >= 3 || dto.getUnpaidFinesAmount().compareTo(fineBlockThreshold) >= 0) {
                dto.setTier(Tier.RESTRICTED);
                dto.setExplanation("Restricted standing due to " + dto.getActiveOverdueCount() +
                        " overdue loans and ₹" + dto.getUnpaidFinesAmount() + " in outstanding fines.");
            } else if ((dto.getTotalReturns12m() > 0 && dto.getOnTimeRatePercent() < 75.0) ||
                    dto.getActiveOverdueCount() >= 2 ||
                    (dto.getUnpaidFinesAmount().compareTo(BigDecimal.ZERO) > 0 && dto.getOldestUnpaidFineDays() > 30)) {
                dto.setTier(Tier.WATCHLIST);
                dto.setExplanation("On watchlist: on-time return rate is " + dto.getOnTimeRatePercent() +
                        "% with " + dto.getActiveOverdueCount() + " overdue loans.");
            } else if (dto.getTotalReturns12m() >= minReturnsForExemplary &&
                    dto.getOnTimeRatePercent() >= 95.0 &&
                    dto.getActiveOverdueCount() == 0 &&
                    dto.getUnpaidFinesAmount().compareTo(BigDecimal.ZERO) == 0 &&
                    dto.getLostOrDamagedCount() == 0) {
                dto.setTier(Tier.EXEMPLARY);
                dto.setExplanation("Exemplary standing: " + dto.getOnTimeRatePercent() +
                        "% on-time returns, zero overdue loans, zero unpaid fines, and flawless history.");
            } else {
                dto.setTier(Tier.STANDARD);
                dto.setExplanation("Standard academic standing with active library participation.");
            }

            // Eligible for goodwill only if Exemplary and hasn't used it yet
            dto.setEligibleForGoodwill(dto.getTier() == Tier.EXEMPLARY && !used);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to compute trust standing for student #" + studentId, e);
            dto.setTier(Tier.STANDARD);
            dto.setExplanation("Default standing applied due to lookup fault.");
        }

        return dto;
    }
}
