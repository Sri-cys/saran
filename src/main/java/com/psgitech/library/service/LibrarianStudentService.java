package com.psgitech.library.service;

import com.psgitech.library.dto.librarian.NoDuesSlipDTO;
import com.psgitech.library.exception.ValidationException;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.model.FineRecord;
import com.psgitech.library.model.Reservation;
import com.psgitech.library.repository.LibrarianStudentRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service managing student catalog lookups, multi-module profile audits, and No-Dues clearance.
 */
public class LibrarianStudentService {
    private final LibrarianStudentRepository repository;
    private final BorrowingService borrowingService;
    private final FineService fineService;
    private final ReservationService reservationService;
    private final BookInterchangeService interchangeService;
    private final ActivityLogService activityLogService;
    private final TrustStandingService trustStandingService;

    public LibrarianStudentService() {
        this.repository = new LibrarianStudentRepository();
        this.borrowingService = new BorrowingService();
        this.fineService = new FineService();
        this.reservationService = new ReservationService();
        this.interchangeService = new BookInterchangeService();
        this.activityLogService = new ActivityLogService();
        this.trustStandingService = new TrustStandingService();
    }

    public LibrarianStudentService(LibrarianStudentRepository repository,
                                   BorrowingService borrowingService,
                                   FineService fineService,
                                   ReservationService reservationService,
                                   BookInterchangeService interchangeService,
                                   ActivityLogService activityLogService) {
        this.repository = repository;
        this.borrowingService = borrowingService;
        this.fineService = fineService;
        this.reservationService = reservationService;
        this.interchangeService = interchangeService;
        this.activityLogService = activityLogService;
        this.trustStandingService = new TrustStandingService();
    }

    public List<Map<String, Object>> getStudents(int page, int pageSize, String query, String filter) {
        int clampedPage = Math.max(1, page);
        int clampedSize = Math.max(1, Math.min(100, pageSize));
        int offset = (clampedPage - 1) * clampedSize;
        return repository.findStudents(offset, clampedSize, query, filter);
    }

    public int countStudents(String query, String filter) {
        return repository.countStudents(query, filter);
    }

    public Map<String, Object> getStudentProfile(int studentId) {
        Map<String, Object> profile = new HashMap<>();

        List<BorrowTransaction> activeLoans = borrowingService.getStudentActiveBorrowings(studentId);
        List<BorrowTransaction> history = borrowingService.getStudentBorrowingHistory(studentId);
        List<Reservation> reservations = reservationService.getActiveReservationsForStudent(studentId);
        List<FineRecord> fines = fineService.getFinesByStudent(studentId);

        profile.put("activeLoans", activeLoans);
        profile.put("borrowingHistory", history);
        profile.put("activeReservations", reservations);
        profile.put("fines", fines);
        profile.put("incomingTransfers", interchangeService.getIncomingRequests(studentId));
        profile.put("outgoingTransfers", interchangeService.getOutgoingRequests(studentId));
        profile.put("trustStanding", trustStandingService.computeTrustStanding(studentId));

        return profile;
    }

    public NoDuesSlipDTO generateNoDuesSlip(int studentId, int staffUserId, String staffName, String ipAddress) {
        NoDuesSlipDTO slip = repository.checkNoDuesClearance(studentId, staffUserId, staffName);
        if (slip != null) {
            activityLogService.logEvent(
                    "NO_DUES_CHECK", staffUserId, "LIBRARIAN", "STUDENT",
                    String.valueOf(studentId),
                    "No-Dues Clearance Checked: Eligible=" + slip.isEligible() + ", Ref=" + slip.getCertificateReference(),
                    ipAddress
            );
        }
        return slip;
    }

    public boolean toggleStudentStatus(int studentId, boolean active, String reason, int staffUserId, String ipAddress) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException("Mandatory reason is required to change student account status.");
        }
        boolean ok = repository.updateStudentStatus(studentId, active);
        if (ok) {
            activityLogService.logEvent(
                    active ? "STUDENT_ACTIVATED" : "STUDENT_DEACTIVATED",
                    staffUserId, "LIBRARIAN", "STUDENT",
                    String.valueOf(studentId),
                    "Status changed to " + (active ? "ACTIVE" : "INACTIVE") + ". Reason: " + reason.trim(),
                    ipAddress
            );
        }
        return ok;
    }
}
