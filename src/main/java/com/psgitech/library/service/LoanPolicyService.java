package com.psgitech.library.service;

import com.psgitech.library.exception.StaleVersionConflictException;
import com.psgitech.library.model.LoanPolicyChangeRecord;
import com.psgitech.library.model.LoanPolicySettings;
import com.psgitech.library.model.LoanPolicyWindow;
import com.psgitech.library.model.PagedResult;
import com.psgitech.library.repository.LoanPolicyRepository;
import com.psgitech.library.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service orchestrating administrative management of dynamic loan policies, version audits,
 * and dry-run preview simulations.
 */
public class LoanPolicyService {

    private static final Logger LOGGER = Logger.getLogger(LoanPolicyService.class.getName());

    private final LoanPolicyRepository repository;

    public LoanPolicyService() {
        this(new LoanPolicyRepository());
    }

    public LoanPolicyService(LoanPolicyRepository repository) {
        this.repository = repository;
    }

    public LoanPolicySettings getSettings() {
        return repository.getSettings();
    }

    public LoanPolicySettings updateSettings(LoanPolicySettings newSettings, int expectedVersion,
                                             Integer adminUserId, String adminName) {
        if (newSettings == null) {
            throw new IllegalArgumentException("Loan policy settings cannot be null.");
        }
        LoanPolicySettings.validate(
                newSettings.getEnterThreshold(),
                newSettings.getExitThreshold(),
                newSettings.getHighDemandDays(),
                expectedVersion
        );

        Connection conn = null;
        boolean originalAutoCommit = true;

        try {
            conn = DBConnection.getConnection();
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // 1. Read existing settings for audit
            LoanPolicySettings current = repository.getSettings(conn);

            // 2. Conditional optimistic update
            boolean updated = repository.updateSettings(conn, newSettings, expectedVersion);
            if (!updated) {
                conn.rollback();
                throw new StaleVersionConflictException("Settings have been updated by another administrator (version conflict). Please refresh the page and try again.");
            }

            // 3. Insert audit log in SAME transaction
            LoanPolicyChangeRecord change = new LoanPolicyChangeRecord(
                    adminUserId,
                    adminName != null ? adminName : "Administrator",
                    current.isEnabled(),
                    newSettings.isEnabled(),
                    current.getEnterThreshold(),
                    newSettings.getEnterThreshold(),
                    current.getExitThreshold(),
                    newSettings.getExitThreshold(),
                    current.getHighDemandDays(),
                    newSettings.getHighDemandDays(),
                    expectedVersion + 1
            );
            repository.insertChangeHistory(conn, change);

            conn.commit();
            LOGGER.info("Loan policy settings updated to version " + (expectedVersion + 1) + " by user #" + adminUserId);

            return repository.getSettings();

        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { LOGGER.log(Level.SEVERE, "Rollback failed", ex); }
            }
            LOGGER.log(Level.SEVERE, "Failed to update loan policy settings", e);
            throw new RuntimeException("Database error updating loan policy settings: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    public PagedResult<Map<String, Object>> getHighDemandBooks(int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 20;
        int offset = (page - 1) * pageSize;

        List<Map<String, Object>> items = repository.listHighDemandBooks(offset, pageSize);
        int total = repository.countHighDemandBooks();
        return new PagedResult<>(items, page, pageSize, total);
    }

    public PagedResult<LoanPolicyChangeRecord> getChangeHistory(int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 20;
        int offset = (page - 1) * pageSize;

        List<LoanPolicyChangeRecord> items = repository.getChangeHistory(offset, pageSize);
        int total = repository.countChangeHistory();
        return new PagedResult<>(items, page, pageSize, total);
    }

    public int previewProposedSettings(int enterThreshold, int exitThreshold, int highDemandDays) {
        return repository.previewProposedSettings(enterThreshold, exitThreshold, highDemandDays);
    }

    public List<LoanPolicyWindow> listWindows() {
        return repository.listWindows();
    }

    public boolean createWindow(LoanPolicyWindow window) {
        if (window == null) {
            throw new IllegalArgumentException("Window definition cannot be null.");
        }
        if (window.getStartDate() == null || window.getEndDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required.");
        }
        if (window.getStartDate().isAfter(window.getEndDate())) {
            throw new IllegalArgumentException("Start date cannot be after end date.");
        }
        if (window.getEnterThresholdOverride() < 2) {
            throw new IllegalArgumentException("Enter threshold override must be at least 2.");
        }
        return repository.createWindow(window);
    }

    public boolean deactivateWindow(int windowId) {
        return repository.deactivateWindow(windowId);
    }
}
