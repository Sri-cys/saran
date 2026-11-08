package com.psgitech.library.service;

import com.psgitech.library.enums.NotificationType;
import com.psgitech.library.model.NotificationDraft;
import com.psgitech.library.util.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service scanning active loans for due-date milestones and queuing idempotent
 * DUE_SOON and OVERDUE reminders.
 */
public class NotificationReminderService {
    private static final Logger LOGGER = Logger.getLogger(NotificationReminderService.class.getName());

    private final NotificationRecorder notificationRecorder;

    public NotificationReminderService() {
        this.notificationRecorder = new NotificationRecorder();
    }

    public NotificationReminderService(NotificationRecorder notificationRecorder) {
        this.notificationRecorder = notificationRecorder;
    }

    public int processAllReminders() {
        int dueSoonCount = scanDueSoonReminders();
        int overdueCount = scanOverdueReminders();
        return dueSoonCount + overdueCount;
    }

    public int scanDueSoonReminders() {
        String sql = "SELECT bt.transaction_id, bt.student_id, bt.book_id, b.title AS book_title, " +
                "bt.due_date, u.email " +
                "FROM borrow_transactions bt " +
                "JOIN books b ON bt.book_id = b.book_id " +
                "JOIN users u ON bt.student_id = u.user_id " +
                "WHERE bt.status = 'ACTIVE' " +
                "AND bt.due_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 2 DAY)";

        int queued = 0;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int txId = rs.getInt("transaction_id");
                    int studentId = rs.getInt("student_id");
                    int bookId = rs.getInt("book_id");
                    String bookTitle = rs.getString("book_title");
                    Date dueDate = rs.getDate("due_date");
                    String email = rs.getString("email");

                    String dedupeKey = "DUE_SOON:" + txId + ":" + LocalDate.now();

                    NotificationDraft draft = NotificationDraft.builder(NotificationType.DUE_SOON, studentId, dedupeKey)
                            .recipientEmail(email)
                            .entity("BORROW_TRANSACTION", txId)
                            .param("bookTitle", bookTitle)
                            .param("dueDate", dueDate != null ? dueDate.toString() : "")
                            .param("bookId", String.valueOf(bookId))
                            .build();

                    notificationRecorder.record(conn, draft);
                    queued++;
                }
            }
            conn.commit();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error scanning due soon reminders", e);
        }
        return queued;
    }

    public int scanOverdueReminders() {
        String sql = "SELECT bt.transaction_id, bt.student_id, bt.book_id, b.title AS book_title, " +
                "bt.due_date, u.email, DATEDIFF(CURDATE(), bt.due_date) AS days_overdue " +
                "FROM borrow_transactions bt " +
                "JOIN books b ON bt.book_id = b.book_id " +
                "JOIN users u ON bt.student_id = u.user_id " +
                "WHERE bt.status = 'ACTIVE' " +
                "AND bt.due_date < CURDATE() " +
                "AND DATEDIFF(CURDATE(), bt.due_date) IN (1, 7, 14)";

        int queued = 0;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int txId = rs.getInt("transaction_id");
                    int studentId = rs.getInt("student_id");
                    int bookId = rs.getInt("book_id");
                    String bookTitle = rs.getString("book_title");
                    Date dueDate = rs.getDate("due_date");
                    int daysOverdue = rs.getInt("days_overdue");
                    String email = rs.getString("email");

                    String dedupeKey = "OVERDUE:" + txId + ":DAY" + daysOverdue;

                    NotificationDraft draft = NotificationDraft.builder(NotificationType.OVERDUE, studentId, dedupeKey)
                            .recipientEmail(email)
                            .entity("BORROW_TRANSACTION", txId)
                            .param("bookTitle", bookTitle)
                            .param("dueDate", dueDate != null ? dueDate.toString() : "")
                            .param("daysOverdue", String.valueOf(daysOverdue))
                            .param("bookId", String.valueOf(bookId))
                            .build();

                    notificationRecorder.record(conn, draft);
                    queued++;
                }
            }
            conn.commit();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error scanning overdue reminders", e);
        }
        return queued;
    }
}
