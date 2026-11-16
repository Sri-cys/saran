package com.psgitech.library.policy;

import com.psgitech.library.enums.CaseStatus;
import com.psgitech.library.enums.NotificationType;
import com.psgitech.library.exception.InvalidCaseStateException;
import com.psgitech.library.exception.IsbnMismatchException;
import com.psgitech.library.exception.ReplacementRejectedException;
import com.psgitech.library.model.*;
import com.psgitech.library.repository.BookRepository;
import com.psgitech.library.repository.LossDamageCaseRepository;
import com.psgitech.library.service.AllocationService;
import com.psgitech.library.service.NotificationRecorder;
import com.psgitech.library.util.ValidationUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Concrete PenaltyResolution strategy resolving through physical copy replacement.
 * Enforces ISBN and physical condition verification.
 * In ONE atomic transaction:
 * - Adds a new physical copy of the same book
 * - Offers it to the waiting reservation queue (or shelf)
 * - Resolves the case as RESOLVED_REPLACED
 */
public class PhysicalReplacement extends PenaltyResolution {
    private static final Logger LOGGER = Logger.getLogger(PhysicalReplacement.class.getName());

    private final LossDamageCaseRepository caseRepository;
    private final BookRepository bookRepository;
    private final AllocationService allocationService;
    private final NotificationRecorder notificationRecorder;

    private BookCopy createdCopy;
    private AllocationResult allocationResult;

    public PhysicalReplacement(LossDamageCaseRepository caseRepository,
                               BookRepository bookRepository,
                               AllocationService allocationService) {
        this.caseRepository = caseRepository;
        this.bookRepository = bookRepository;
        this.allocationService = allocationService;
        this.notificationRecorder = new NotificationRecorder();
    }

    @Override
    protected void validate(Connection conn, LossDamageCase caseRecord, ResolutionContext context) throws SQLException {
        CaseStatus current = caseRecord.getStatus();
        if (current != CaseStatus.REPLACEMENT_SUBMITTED && current != CaseStatus.REPLACEMENT_PENDING) {
            throw new InvalidCaseStateException("Cannot accept replacement: case #" + caseRecord.getCaseId() +
                    " is in status " + current + " (expected REPLACEMENT_SUBMITTED or REPLACEMENT_PENDING).");
        }

        // 1. Condition Verification
        if (!context.isConditionOk()) {
            throw new ReplacementRejectedException("Replacement copy must be verified to be in good condition, complete, and unmarked.");
        }

        // 2. Fetch catalog book metadata for ISBN verification
        String isbn = null;
        String title = "";
        String sql = "SELECT isbn, title FROM books WHERE book_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, caseRecord.getBookId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    isbn = rs.getString("isbn");
                    title = rs.getString("title");
                }
            }
        }

        // 3. ISBN / Title Verification
        boolean hasCatalogIsbn = (isbn != null && !isbn.trim().isEmpty() && !isbn.toUpperCase().startsWith("NOISBN"));
        if (hasCatalogIsbn) {
            String normCatalog = ValidationUtil.normalizeIsbn(isbn);
            String normScanned = ValidationUtil.normalizeIsbn(context.getScannedIsbn());
            if (normScanned.isEmpty() || !normCatalog.equals(normScanned)) {
                throw new IsbnMismatchException("Scanned ISBN (" + (context.getScannedIsbn() != null ? context.getScannedIsbn() : "none") +
                        ") does not match book ISBN (" + isbn + ").");
            }
        } else {
            if (!context.isTitleConfirmed()) {
                throw new IllegalArgumentException("Title confirmation required: this catalog title does not have a standard ISBN.");
            }
        }
    }

    @Override
    protected void computeOrConfirmAmount(Connection conn, LossDamageCase caseRecord, ResolutionContext context) {
        // Physical replacement satisfies the obligation in full without monetary debt
    }

    @Override
    protected void applyResolutionStep(Connection conn, LossDamageCase caseRecord, ResolutionContext context) throws SQLException {
        // 1. Create new physical copy of the SAME book
        this.createdCopy = bookRepository.addSingleCopy(conn, caseRecord.getBookId());

        // 2. Offer to queue or make available
        this.allocationResult = allocationService.offerNewCopy(conn, createdCopy.getCopyId(), caseRecord.getBookId());
    }

    @Override
    protected void updateRecords(Connection conn, LossDamageCase caseRecord, ResolutionContext context) throws SQLException {
        CaseStatus oldStatus = caseRecord.getStatus();
        caseRecord.acceptReplacement(createdCopy.getCopyId(), context.getActorUserId());
        caseRepository.updateCase(conn, caseRecord, oldStatus);
    }

    @Override
    protected void auditAndNotify(Connection conn, LossDamageCase caseRecord, ResolutionContext context) throws SQLException {
        // 1. Audit event
        CaseEvent event = new CaseEvent(
                caseRecord.getCaseId(),
                "REPLACEMENT_ACCEPTED",
                caseRecord.getStatus().name(),
                CaseStatus.RESOLVED_REPLACED.name(),
                context.getActorUserId(),
                context.getActorRole(),
                "Replacement accepted. Created copy #" + createdCopy.getCopyId() + " (" + createdCopy.getCopyCode() + ")."
        );
        caseRepository.recordEvent(conn, event);

        // 2. Notification to student
        try {
            NotificationDraft draft = NotificationDraft
                    .builder(NotificationType.REPLACEMENT_ACCEPTED, caseRecord.getStudentId(), "REP_ACCEPTED:" + caseRecord.getCaseId())
                    .entity("CASE", caseRecord.getCaseId())
                    .param("bookTitle", caseRecord.getBookTitle() != null ? caseRecord.getBookTitle() : "Book")
                    .param("copyCode", createdCopy.getCopyCode())
                    .build();
            notificationRecorder.record(conn, draft);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
            LOGGER.log(Level.WARNING, "Failed to record replacement accepted notification", e);
        }
    }

    public BookCopy getCreatedCopy() { return createdCopy; }
    public AllocationResult getAllocationResult() { return allocationResult; }
}
