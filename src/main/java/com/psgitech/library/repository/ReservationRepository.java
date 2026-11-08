package com.psgitech.library.repository;

import com.psgitech.library.config.BorrowingPolicy;
import com.psgitech.library.enums.CopyStatus;
import com.psgitech.library.enums.ReservationStatus;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.*;
import com.psgitech.library.model.Reservation;
import com.psgitech.library.util.DBConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object / Repository for Book Queue Reservations.
 * Enforces JDBC transaction isolation, pessimistic locking (SELECT ... FOR UPDATE),
 * consistent lock ordering (users first, then books), and safe connection/rollback management.
 */
public class ReservationRepository {
    private static final Logger LOGGER = Logger.getLogger(ReservationRepository.class.getName());

    /**
     * Executes an atomic, concurrency-safe Book Reservation creation transaction.
     * Lock ordering: user (student) row first, then book row.
     */
    public Reservation createReservationTransaction(int studentId, int bookId) {
        Connection conn = null;
        boolean originalAutoCommit = true;

        try {
            conn = DBConnection.getConnection();
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // 1. Lock student record first to serialize concurrent requests for the same student
            String studentLockSql = "SELECT user_id, name, role, active, register_number FROM users WHERE user_id = ? FOR UPDATE";
            String studentName = "";
            String registerNumber = "";
            try (PreparedStatement psStudent = conn.prepareStatement(studentLockSql)) {
                psStudent.setInt(1, studentId);
                try (ResultSet rs = psStudent.executeQuery()) {
                    if (!rs.next()) {
                        throw new StudentNotFoundException("Student with ID " + studentId + " does not exist.");
                    }
                    boolean active = rs.getBoolean("active");
                    String role = rs.getString("role");
                    studentName = rs.getString("name");
                    registerNumber = rs.getString("register_number");

                    if (!active) {
                        throw new StudentNotEligibleException("Student account is inactive or suspended. Cannot reserve books.");
                    }
                    if (!"STUDENT".equalsIgnoreCase(role)) {
                        throw new StudentNotEligibleException("User is not a student (Role: " + role + "). Only students may reserve books.");
                    }
                }
            }

            // 2. Lock book record to serialize concurrent queue entries for the same title
            String bookLockSql = "SELECT book_id, title, author, isbn, cover_image_url, is_archived FROM books WHERE book_id = ? FOR UPDATE";
            String bookTitle = "";
            String bookAuthor = "";
            String bookIsbn = "";
            String bookCoverUrl = "";
            try (PreparedStatement psBook = conn.prepareStatement(bookLockSql)) {
                psBook.setInt(1, bookId);
                try (ResultSet rs = psBook.executeQuery()) {
                    if (!rs.next()) {
                        throw new BookNotFoundException("Book with ID " + bookId + " does not exist.");
                    }
                    boolean archived = rs.getBoolean("is_archived");
                    if (archived) {
                        throw new InvalidReservationException("Cannot reserve copies of an archived book title.");
                    }
                    bookTitle = rs.getString("title");
                    bookAuthor = rs.getString("author");
                    bookIsbn = rs.getString("isbn");
                    bookCoverUrl = rs.getString("cover_image_url");
                }
            }

            // 3. Inspect copy availability under locked book boundary
            int reservableCopies = 0;
            int availableCopies = 0;
            String copyCountSql = "SELECT status, COUNT(*) AS cnt FROM book_copies WHERE book_id = ? GROUP BY status";
            try (PreparedStatement psCopies = conn.prepareStatement(copyCountSql)) {
                psCopies.setInt(1, bookId);
                try (ResultSet rs = psCopies.executeQuery()) {
                    while (rs.next()) {
                        String statusStr = rs.getString("status");
                        int cnt = rs.getInt("cnt");
                        CopyStatus cs = CopyStatus.fromString(statusStr);
                        if (cs != CopyStatus.ARCHIVED) {
                            reservableCopies += cnt;
                        }
                        if (cs == CopyStatus.AVAILABLE) {
                            availableCopies += cnt;
                        }
                    }
                }
            }

            if (reservableCopies == 0) {
                throw new InvalidReservationException("This book has no physical copies in active circulation to reserve.");
            }

            // Rule 5.2 (5): Zero AVAILABLE copies required. Otherwise reject with exact wording.
            if (availableCopies > 0) {
                throw new InvalidReservationException("Copies of this book are currently available for borrowing at the library desk. Reservations are only permitted when all copies are checked out.");
            }

            // 4. Verify student does not currently hold an ACTIVE borrow for this book
            String activeBorrowSql = "SELECT COUNT(*) FROM borrow_transactions WHERE student_id = ? AND book_id = ? AND status = 'ACTIVE'";
            try (PreparedStatement psBorrow = conn.prepareStatement(activeBorrowSql)) {
                psBorrow.setInt(1, studentId);
                psBorrow.setInt(2, bookId);
                try (ResultSet rs = psBorrow.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new InvalidReservationException("You are currently borrowing an active copy of this book. Students cannot hold both a borrow loan and a reservation for the same title.");
                    }
                }
            }

            // 5. Verify student has no WAITING or ALLOCATED reservation for this book
            String existingResSql = "SELECT COUNT(*) FROM reservations WHERE student_id = ? AND book_id = ? AND status IN ('WAITING', 'ALLOCATED')";
            try (PreparedStatement psRes = conn.prepareStatement(existingResSql)) {
                psRes.setInt(1, studentId);
                psRes.setInt(2, bookId);
                try (ResultSet rs = psRes.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        throw new DuplicateReservationException("You already hold an active reservation for this book.");
                    }
                }
            }

            // 6. Check student total active reservations quota
            int maxLimit = BorrowingPolicy.getMaxActiveReservationLimit();
            String activeCountSql = "SELECT COUNT(*) FROM reservations WHERE student_id = ? AND status IN ('WAITING', 'ALLOCATED')";
            try (PreparedStatement psCount = conn.prepareStatement(activeCountSql)) {
                psCount.setInt(1, studentId);
                try (ResultSet rs = psCount.executeQuery()) {
                    if (rs.next() && rs.getInt(1) >= maxLimit) {
                        throw new ReservationLimitExceededException("You have reached the maximum active reservation limit (" + maxLimit + " active reservations).");
                    }
                }
            }

            // 7. Insert the new WAITING reservation
            LocalDate today = LocalDate.now();
            String insertSql = "INSERT INTO reservations (student_id, book_id, reservation_date, status) VALUES (?, ?, ?, 'WAITING')";
            int generatedId;
            Timestamp createdAt;
            try (PreparedStatement psInsert = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                psInsert.setInt(1, studentId);
                psInsert.setInt(2, bookId);
                psInsert.setDate(3, java.sql.Date.valueOf(today));

                int rows = psInsert.executeUpdate();
                if (rows == 0) {
                    throw new SQLException("Failed to insert reservation record.");
                }

                try (ResultSet gk = psInsert.getGeneratedKeys()) {
                    if (gk.next()) {
                        generatedId = gk.getInt(1);
                    } else {
                        throw new SQLException("Failed to obtain generated reservation ID.");
                    }
                }
            }

            // Fetch created_at timestamp
            String tsSql = "SELECT created_at FROM reservations WHERE reservation_id = ?";
            try (PreparedStatement psTs = conn.prepareStatement(tsSql)) {
                psTs.setInt(1, generatedId);
                try (ResultSet rs = psTs.executeQuery()) {
                    if (rs.next()) {
                        createdAt = rs.getTimestamp("created_at");
                    } else {
                        createdAt = new Timestamp(System.currentTimeMillis());
                    }
                }
            }

            // 8. Compute FIFO position and total waiting count inside transaction
            int queuePosition = 1;
            String posSql = "SELECT COUNT(*) FROM reservations WHERE book_id = ? AND status = 'WAITING' AND (created_at < ? OR (created_at = ? AND reservation_id <= ?))";
            try (PreparedStatement psPos = conn.prepareStatement(posSql)) {
                psPos.setInt(1, bookId);
                psPos.setTimestamp(2, createdAt);
                psPos.setTimestamp(3, createdAt);
                psPos.setInt(4, generatedId);
                try (ResultSet rs = psPos.executeQuery()) {
                    if (rs.next()) {
                        queuePosition = rs.getInt(1);
                    }
                }
            }

            int totalWaiting = queuePosition;
            String totalSql = "SELECT COUNT(*) FROM reservations WHERE book_id = ? AND status = 'WAITING'";
            try (PreparedStatement psTotal = conn.prepareStatement(totalSql)) {
                psTotal.setInt(1, bookId);
                try (ResultSet rs = psTotal.executeQuery()) {
                    if (rs.next()) {
                        totalWaiting = rs.getInt(1);
                    }
                }
            }

            // Record Feature 11: RESERVATION_CREATED
            try {
                com.psgitech.library.model.NotificationDraft notifDraft = com.psgitech.library.model.NotificationDraft
                        .builder(com.psgitech.library.enums.NotificationType.RESERVATION_CREATED, studentId, "RES_CREATED:" + generatedId)
                        .entity("RESERVATION", generatedId)
                        .param("bookTitle", bookTitle)
                        .param("queuePosition", String.valueOf(queuePosition))
                        .build();
                new com.psgitech.library.service.NotificationRecorder().record(conn, notifDraft);
            } catch (SQLException e) {
                if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
                LOGGER.log(Level.WARNING, "Failed to record reservation notification", e);
            }

            conn.commit();

            Reservation res = new Reservation(generatedId, studentId, bookId, today,
                    ReservationStatus.WAITING, null, null, createdAt, createdAt);
            res.setStudentName(studentName);
            res.setRegisterNumber(registerNumber);
            res.setBookTitle(bookTitle);
            res.setBookAuthor(bookAuthor);
            res.setBookIsbn(bookIsbn);
            res.setCoverImageUrl(bookCoverUrl);
            res.setQueuePosition(queuePosition);
            res.setTotalWaitingInQueue(totalWaiting);

            return res;

        } catch (SQLIntegrityConstraintViolationException e) {
            safeRollback(conn, e);
            throw new DuplicateReservationException("You already hold an active reservation for this book.");

        } catch (SQLException e) {
            safeRollback(conn, e);
            LOGGER.log(Level.SEVERE, "Database error during reservation creation", e);
            throw new RuntimeException("Database error during reservation: " + e.getMessage(), e);

        } catch (RuntimeException e) {
            safeRollback(conn, e);
            throw e;

        } finally {
            closeConnection(conn, originalAutoCommit);
        }
    }

    /**
     * Executes an atomic cancellation transaction.
     * Enforces ownership / role check, state transition validation, and audit recording.
     */
    public Reservation cancelReservationTransaction(int reservationId, int actingUserId, Role actingRole) {
        Connection conn = null;
        boolean originalAutoCommit = true;

        try {
            conn = DBConnection.getConnection();
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // 1. Lock reservation record
            String lockSql = "SELECT r.*, b.title AS book_title, u.name AS student_name, u.register_number " +
                    "FROM reservations r " +
                    "JOIN books b ON r.book_id = b.book_id " +
                    "JOIN users u ON r.student_id = u.user_id " +
                    "WHERE r.reservation_id = ? FOR UPDATE";
            Reservation res = null;
            try (PreparedStatement psLock = conn.prepareStatement(lockSql)) {
                psLock.setInt(1, reservationId);
                try (ResultSet rs = psLock.executeQuery()) {
                    if (!rs.next()) {
                        throw new ReservationNotFoundException("Reservation #" + reservationId + " not found.");
                    }
                    res = mapRow(rs);
                }
            }

            // 2. Ownership / Permission verification
            if (actingRole == Role.STUDENT) {
                if (res.getStudentId() != actingUserId) {
                    // Part 5.4: Return 404 to avoid revealing existence of other students' reservations
                    throw new ReservationNotFoundException("Reservation #" + reservationId + " not found.");
                }
            } else if (actingRole != Role.LIBRARIAN && actingRole != Role.ADMIN) {
                throw new ReservationNotFoundException("Access denied: Insufficient permissions to cancel reservation.");
            }

            // 3. Status validation: Only WAITING can be cancelled
            if (res.getStatus() != ReservationStatus.WAITING) {
                throw new InvalidReservationException("Reservation #" + reservationId + " cannot be cancelled because its current status is " + res.getStatus() + ".");
            }

            // 4. Update status to CANCELLED
            Timestamp now = new Timestamp(System.currentTimeMillis());
            String updateSql = "UPDATE reservations SET status = 'CANCELLED', cancelled_at = ?, cancelled_by = ? WHERE reservation_id = ? AND status = 'WAITING'";
            try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                psUpdate.setTimestamp(1, now);
                psUpdate.setInt(2, actingUserId);
                psUpdate.setInt(3, reservationId);

                int affected = psUpdate.executeUpdate();
                if (affected == 0) {
                    throw new InvalidReservationException("Reservation #" + reservationId + " could not be cancelled. It may have been updated concurrently.");
                }
            }

            // Record Feature 11: RESERVATION_CANCELLED
            try {
                com.psgitech.library.model.NotificationDraft notifDraft = com.psgitech.library.model.NotificationDraft
                        .builder(com.psgitech.library.enums.NotificationType.RESERVATION_CANCELLED, res.getStudentId(), "RES_CANCELLED:" + reservationId)
                        .entity("RESERVATION", reservationId)
                        .param("bookTitle", res.getBookTitle())
                        .build();
                new com.psgitech.library.service.NotificationRecorder().record(conn, notifDraft);
            } catch (SQLException e) {
                if (e.getErrorCode() == 1213 || e.getErrorCode() == 1205) throw e;
                LOGGER.log(Level.WARNING, "Failed to record reservation cancellation notification", e);
            }

            conn.commit();

            res.setStatus(ReservationStatus.CANCELLED);
            res.setCancelledAt(now);
            res.setCancelledBy(actingUserId);
            return res;

        } catch (SQLException e) {
            safeRollback(conn, e);
            LOGGER.log(Level.SEVERE, "Database error during reservation cancellation", e);
            throw new RuntimeException("Database error during reservation cancellation: " + e.getMessage(), e);

        } catch (RuntimeException e) {
            safeRollback(conn, e);
            throw e;

        } finally {
            closeConnection(conn, originalAutoCommit);
        }
    }

    /**
     * Finds active reservations (WAITING or ALLOCATED) for a student with live FIFO positions.
     */
    public List<Reservation> findActiveByStudent(int studentId) {
        String sql = "SELECT r.*, u.name AS student_name, u.register_number, " +
                "b.title AS book_title, b.author AS book_author, b.isbn AS book_isbn, b.cover_image_url, " +
                "a.allocation_id, a.copy_id, bc.copy_code, a.expires_at AS allocation_expires_at, a.allocated_at AS allocation_allocated_at, " +
                "(SELECT COUNT(*) FROM reservations r2 WHERE r2.book_id = r.book_id AND r2.status = 'WAITING' " +
                " AND (r2.created_at < r.created_at OR (r2.created_at = r.created_at AND r2.reservation_id <= r.reservation_id))) AS queue_pos, " +
                "(SELECT COUNT(*) FROM reservations r3 WHERE r3.book_id = r.book_id AND r3.status = 'WAITING') AS total_waiting " +
                "FROM reservations r " +
                "JOIN users u ON r.student_id = u.user_id " +
                "JOIN books b ON r.book_id = b.book_id " +
                "LEFT JOIN allocations a ON r.reservation_id = a.reservation_id AND a.status = 'ALLOCATED' " +
                "LEFT JOIN book_copies bc ON a.copy_id = bc.copy_id " +
                "WHERE r.student_id = ? AND r.status IN ('WAITING', 'ALLOCATED') " +
                "ORDER BY r.created_at ASC";

        List<Reservation> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Reservation r = mapRow(rs);
                    if (r.getStatus() == ReservationStatus.ALLOCATED) {
                        r.setQueuePosition(0);
                        int allocId = rs.getInt("allocation_id");
                        if (!rs.wasNull()) {
                            r.setAllocationId(allocId);
                            r.setCopyId(rs.getInt("copy_id"));
                            r.setCopyCode(rs.getString("copy_code"));
                            r.setAllocationExpiresAt(rs.getTimestamp("allocation_expires_at"));
                            r.setAllocatedAt(rs.getTimestamp("allocation_allocated_at"));
                        }
                    } else {
                        r.setQueuePosition(rs.getInt("queue_pos"));
                    }
                    r.setTotalWaitingInQueue(rs.getInt("total_waiting"));
                    list.add(r);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving active reservations for student " + studentId, e);
            throw new RuntimeException("Database error retrieving active reservations.", e);
        }
        return list;
    }

    /**
     * Finds historical reservations (CANCELLED, FULFILLED, EXPIRED) for a student.
     */
    public List<Reservation> findHistoryByStudent(int studentId) {
        String sql = "SELECT r.*, u.name AS student_name, u.register_number, " +
                "b.title AS book_title, b.author AS book_author, b.isbn AS book_isbn, b.cover_image_url " +
                "FROM reservations r " +
                "JOIN users u ON r.student_id = u.user_id " +
                "JOIN books b ON r.book_id = b.book_id " +
                "WHERE r.student_id = ? AND r.status NOT IN ('WAITING', 'ALLOCATED') " +
                "ORDER BY r.updated_at DESC, r.created_at DESC";

        List<Reservation> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving reservation history for student " + studentId, e);
            throw new RuntimeException("Database error retrieving reservation history.", e);
        }
        return list;
    }

    /**
     * Finds ordered FIFO queue of WAITING reservations for a specific book.
     */
    public List<Reservation> findQueueForBook(int bookId) {
        String sql = "SELECT r.*, u.name AS student_name, u.register_number, " +
                "b.title AS book_title, b.author AS book_author, b.isbn AS book_isbn, b.cover_image_url " +
                "FROM reservations r " +
                "JOIN users u ON r.student_id = u.user_id " +
                "JOIN books b ON r.book_id = b.book_id " +
                "WHERE r.book_id = ? AND r.status = 'WAITING' " +
                "ORDER BY r.created_at ASC, r.reservation_id ASC";

        List<Reservation> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                int pos = 1;
                while (rs.next()) {
                    Reservation r = mapRow(rs);
                    r.setQueuePosition(pos++);
                    list.add(r);
                }
            }
            int total = list.size();
            for (Reservation r : list) {
                r.setTotalWaitingInQueue(total);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving reservation queue for book " + bookId, e);
            throw new RuntimeException("Database error retrieving book queue.", e);
        }
        return list;
    }

    /**
     * Paginated retrieval of all active reservations across the library (for staff desks).
     */
    public List<Reservation> findAllActive(int page, int pageSize, Integer bookIdFilter) {
        int offset = Math.max(0, (page - 1) * pageSize);
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT r.*, u.name AS student_name, u.register_number, ")
          .append("b.title AS book_title, b.author AS book_author, b.isbn AS book_isbn, b.cover_image_url, ")
          .append("(SELECT COUNT(*) FROM reservations r2 WHERE r2.book_id = r.book_id AND r2.status = 'WAITING' ")
          .append(" AND (r2.created_at < r.created_at OR (r2.created_at = r.created_at AND r2.reservation_id <= r.reservation_id))) AS queue_pos, ")
          .append("(SELECT COUNT(*) FROM reservations r3 WHERE r3.book_id = r.book_id AND r3.status = 'WAITING') AS total_waiting ")
          .append("FROM reservations r ")
          .append("JOIN users u ON r.student_id = u.user_id ")
          .append("JOIN books b ON r.book_id = b.book_id ")
          .append("WHERE r.status = 'WAITING' ");

        if (bookIdFilter != null && bookIdFilter > 0) {
            sb.append("AND r.book_id = ? ");
        }

        sb.append("ORDER BY b.title ASC, r.created_at ASC, r.reservation_id ASC ")
          .append("LIMIT ? OFFSET ?");

        List<Reservation> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            int paramIdx = 1;
            if (bookIdFilter != null && bookIdFilter > 0) {
                ps.setInt(paramIdx++, bookIdFilter);
            }
            ps.setInt(paramIdx++, pageSize);
            ps.setInt(paramIdx, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Reservation r = mapRow(rs);
                    r.setQueuePosition(rs.getInt("queue_pos"));
                    r.setTotalWaitingInQueue(rs.getInt("total_waiting"));
                    list.add(r);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving all active reservations", e);
            throw new RuntimeException("Database error retrieving all active reservations.", e);
        }
        return list;
    }

    /**
     * Counts active reservations for a student.
     */
    public int countActiveByStudent(int studentId) {
        String sql = "SELECT COUNT(*) FROM reservations WHERE student_id = ? AND status IN ('WAITING', 'ALLOCATED')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error counting active reservations for student " + studentId, e);
            throw new RuntimeException("Database error counting active reservations.", e);
        }
        return 0;
    }

    /**
     * Computes the dynamic FIFO queue position of a specific reservation.
     */
    public int getQueuePosition(int reservationId) {
        String sql = "SELECT r.book_id, r.created_at, r.status, " +
                "(SELECT COUNT(*) FROM reservations r2 WHERE r2.book_id = r.book_id AND r2.status = 'WAITING' " +
                " AND (r2.created_at < r.created_at OR (r2.created_at = r.created_at AND r2.reservation_id <= r.reservation_id))) AS queue_pos " +
                "FROM reservations r WHERE r.reservation_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (!"WAITING".equalsIgnoreCase(rs.getString("status"))) {
                        return -1;
                    }
                    return rs.getInt("queue_pos");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error computing queue position for reservation " + reservationId, e);
            throw new RuntimeException("Database error computing queue position.", e);
        }
        return -1;
    }

    /**
     * Single batched query retrieving waiting counts for a list of book IDs (avoids N+1 query).
     */
    public Map<Integer, Integer> getWaitingCountsForBooks(List<Integer> bookIds) {
        Map<Integer, Integer> map = new HashMap<>();
        if (bookIds == null || bookIds.isEmpty()) {
            return map;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT book_id, COUNT(*) AS waiting_count FROM reservations WHERE status = 'WAITING' AND book_id IN (");
        for (int i = 0; i < bookIds.size(); i++) {
            sb.append(i == 0 ? "?" : ", ?");
        }
        sb.append(") GROUP BY book_id");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < bookIds.size(); i++) {
                ps.setInt(i + 1, bookIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getInt("book_id"), rs.getInt("waiting_count"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving batched waiting counts", e);
            throw new RuntimeException("Database error retrieving batched waiting counts.", e);
        }
        return map;
    }

    /**
     * Finds a single reservation by ID.
     */
    public Reservation findById(int reservationId) {
        String sql = "SELECT r.*, u.name AS student_name, u.register_number, " +
                "b.title AS book_title, b.author AS book_author, b.isbn AS book_isbn, b.cover_image_url " +
                "FROM reservations r " +
                "JOIN users u ON r.student_id = u.user_id " +
                "JOIN books b ON r.book_id = b.book_id " +
                "WHERE r.reservation_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding reservation by ID " + reservationId, e);
            throw new RuntimeException("Database error finding reservation.", e);
        }
        return null;
    }

    /**
     * Finds active reservation of a student for a specific book, if one exists.
     */
    public Reservation findActiveByStudentAndBook(int studentId, int bookId) {
        String sql = "SELECT r.*, u.name AS student_name, u.register_number, " +
                "b.title AS book_title, b.author AS book_author, b.isbn AS book_isbn, b.cover_image_url, " +
                "(SELECT COUNT(*) FROM reservations r2 WHERE r2.book_id = r.book_id AND r2.status = 'WAITING' " +
                " AND (r2.created_at < r.created_at OR (r2.created_at = r.created_at AND r2.reservation_id <= r.reservation_id))) AS queue_pos, " +
                "(SELECT COUNT(*) FROM reservations r3 WHERE r3.book_id = r.book_id AND r3.status = 'WAITING') AS total_waiting " +
                "FROM reservations r " +
                "JOIN users u ON r.student_id = u.user_id " +
                "JOIN books b ON r.book_id = b.book_id " +
                "WHERE r.student_id = ? AND r.book_id = ? AND r.status IN ('WAITING', 'ALLOCATED')";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, studentId);
            ps.setInt(2, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Reservation r = mapRow(rs);
                    r.setQueuePosition(rs.getInt("queue_pos"));
                    r.setTotalWaitingInQueue(rs.getInt("total_waiting"));
                    return r;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error finding active reservation for student and book", e);
            throw new RuntimeException("Database error finding active reservation.", e);
        }
        return null;
    }

    /**
     * Locks and returns all WAITING reservations for a book in FIFO order (created_at ASC, reservation_id ASC).
     */
    public List<Reservation> lockWaitingByBookFifo(Connection conn, int bookId) throws SQLException {
        List<Reservation> list = new ArrayList<>();
        String sql = "SELECT r.*, u.name AS student_name, u.register_number, u.active AS student_active, " +
                "b.title AS book_title, b.isbn AS book_isbn, b.cover_image_url " +
                "FROM reservations r " +
                "JOIN users u ON r.student_id = u.user_id " +
                "JOIN books b ON r.book_id = b.book_id " +
                "WHERE r.book_id = ? AND r.status = 'WAITING' " +
                "ORDER BY r.created_at ASC, r.reservation_id ASC FOR UPDATE";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Locks a single reservation by ID.
     */
    public Reservation lockById(Connection conn, int reservationId) throws SQLException {
        String sql = "SELECT r.*, u.name AS student_name, u.register_number, " +
                "b.title AS book_title, b.isbn AS book_isbn, b.cover_image_url " +
                "FROM reservations r " +
                "JOIN users u ON r.student_id = u.user_id " +
                "JOIN books b ON r.book_id = b.book_id " +
                "WHERE r.reservation_id = ? FOR UPDATE";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Conditionally updates reservation from WAITING to ALLOCATED.
     * Enforces affected rows == 1.
     */
    public boolean markAllocated(Connection conn, int reservationId) throws SQLException {
        String sql = "UPDATE reservations SET status = 'ALLOCATED', updated_at = CURRENT_TIMESTAMP " +
                "WHERE reservation_id = ? AND status = 'WAITING'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new com.psgitech.library.exception.InvalidReservationException(
                        "Reservation #" + reservationId + " could not be marked ALLOCATED (concurrent state modification).");
            }
            return true;
        }
    }

    /**
     * Conditionally updates reservation from ALLOCATED to FULFILLED upon desk collection.
     * Enforces affected rows == 1.
     */
    public boolean markFulfilled(Connection conn, int reservationId) throws SQLException {
        String sql = "UPDATE reservations SET status = 'FULFILLED', updated_at = CURRENT_TIMESTAMP " +
                "WHERE reservation_id = ? AND status = 'ALLOCATED'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reservationId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new com.psgitech.library.exception.InvalidReservationException(
                        "Reservation #" + reservationId + " could not be marked FULFILLED (concurrent state modification).");
            }
            return true;
        }
    }

    /**
     * Conditionally updates reservation from ALLOCATED to CANCELLED upon release.
     * Enforces affected rows == 1.
     */
    public boolean markCancelledFromAllocated(Connection conn, int reservationId, int actingUserId, Timestamp cancelledAt) throws SQLException {
        String sql = "UPDATE reservations SET status = 'CANCELLED', cancelled_at = ?, cancelled_by = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE reservation_id = ? AND status = 'ALLOCATED'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, cancelledAt != null ? cancelledAt : new Timestamp(System.currentTimeMillis()));
            ps.setInt(2, actingUserId);
            ps.setInt(3, reservationId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new com.psgitech.library.exception.InvalidReservationException(
                        "Reservation #" + reservationId + " could not be cancelled from ALLOCATED state.");
            }
            return true;
        }
    }

    public boolean markExpired(Connection conn, int reservationId, Timestamp expiredAt) throws SQLException {
        String sql = "UPDATE reservations SET status = 'EXPIRED', cancelled_at = ?, updated_at = CURRENT_TIMESTAMP " +
                "WHERE reservation_id = ? AND status = 'ALLOCATED'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, expiredAt != null ? expiredAt : new Timestamp(System.currentTimeMillis()));
            ps.setInt(2, reservationId);
            return ps.executeUpdate() > 0;
        }
    }

    private Reservation mapRow(ResultSet rs) throws SQLException {
        Reservation r = new Reservation();
        r.setReservationId(rs.getInt("reservation_id"));
        r.setStudentId(rs.getInt("student_id"));
        r.setBookId(rs.getInt("book_id"));

        java.sql.Date resDate = rs.getDate("reservation_date");
        if (resDate != null) r.setReservationDate(resDate.toLocalDate());

        r.setStatus(ReservationStatus.fromString(rs.getString("status")));
        r.setCancelledAt(rs.getTimestamp("cancelled_at"));

        int cancelledBy = rs.getInt("cancelled_by");
        if (!rs.wasNull()) r.setCancelledBy(cancelledBy);

        r.setCreatedAt(rs.getTimestamp("created_at"));
        r.setUpdatedAt(rs.getTimestamp("updated_at"));

        try {
            r.setStudentName(rs.getString("student_name"));
            r.setRegisterNumber(rs.getString("register_number"));
            r.setBookTitle(rs.getString("book_title"));
            r.setBookAuthor(rs.getString("book_author"));
            r.setBookIsbn(rs.getString("book_isbn"));
            r.setCoverImageUrl(rs.getString("cover_image_url"));
        } catch (SQLException ignored) {
            // Optional columns if joined
        }

        return r;
    }

    private void safeRollback(Connection conn, Exception originalEx) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                originalEx.addSuppressed(rollbackEx);
                LOGGER.log(Level.SEVERE, "Rollback failed", rollbackEx);
            }
        }
    }

    private void closeConnection(Connection conn, boolean originalAutoCommit) {
        if (conn != null) {
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException ex) {
                LOGGER.log(Level.FINE, "Failed to restore original autoCommit state", ex);
            }
            try {
                conn.close();
            } catch (SQLException ex) {
                LOGGER.log(Level.FINE, "Error closing connection", ex);
            }
        }
    }
}
