package com.psgitech.library.service;

import com.psgitech.library.enums.NotificationCategory;
import com.psgitech.library.enums.NotificationType;
import com.psgitech.library.exception.ValidationException;
import com.psgitech.library.model.LibraryQuery;
import com.psgitech.library.model.Notification;
import com.psgitech.library.repository.NotificationRepository;
import com.psgitech.library.repository.QueryRepository;
import com.psgitech.library.util.DBConnection;

import java.sql.Connection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QueryService {
    private static final Logger LOGGER = Logger.getLogger(QueryService.class.getName());

    private final QueryRepository queryRepository;
    private final NotificationRepository notificationRepository;
    private final ActivityLogService activityLogService;

    public QueryService() {
        this.queryRepository = new QueryRepository();
        this.notificationRepository = new NotificationRepository();
        this.activityLogService = new ActivityLogService();
    }

    public QueryService(QueryRepository queryRepository, NotificationRepository notificationRepository, ActivityLogService activityLogService) {
        this.queryRepository = queryRepository;
        this.notificationRepository = notificationRepository;
        this.activityLogService = activityLogService;
    }

    public List<LibraryQuery> getQueries(String status, String category, int page, int pageSize) {
        int clampedPage = Math.max(1, page);
        int clampedSize = Math.max(1, Math.min(100, pageSize));
        int offset = (clampedPage - 1) * clampedSize;
        return queryRepository.findQueries(status, category, offset, clampedSize);
    }

    public LibraryQuery getById(int queryId) {
        return queryRepository.findById(queryId);
    }

    public boolean replyQuery(int queryId, String responseText, int staffId) {
        if (responseText == null || responseText.trim().isEmpty()) {
            throw new ValidationException("Reply message cannot be empty.");
        }

        LibraryQuery query = queryRepository.findById(queryId);
        if (query == null) {
            throw new ValidationException("Query #" + queryId + " not found.");
        }

        boolean updated = queryRepository.replyQuery(queryId, responseText.trim(), staffId);
        if (updated) {
            // Notify student in-app
            try (Connection conn = DBConnection.getConnection()) {
                Notification notif = new Notification();
                notif.setRecipientUserId(query.getStudentId());
                notif.setCategory(NotificationCategory.SYSTEM);
                notif.setNotificationType(NotificationType.QUERY_REPLIED);
                notif.setTitle("Library Inquiry Resolved: " + query.getSubject());
                notif.setMessage("A librarian has replied to your query '" + query.getSubject() + "': " + responseText.trim());
                notif.setDeduplicationKey("QUERY_REPLY_" + queryId + "_" + System.currentTimeMillis());
                notif.setRead(false);
                notificationRepository.insert(conn, notif);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to send notification for query reply", e);
            }

            activityLogService.logEvent("STUDENT_QUERY_RESOLVED", staffId, "LIBRARIAN",
                    "LIBRARY_QUERY", String.valueOf(queryId),
                    "Resolved inquiry #" + queryId + " from student #" + query.getStudentId(), null);
        }
        return updated;
    }

    public int submitQuery(LibraryQuery q) {
        if (q.getSubject() == null || q.getSubject().trim().isEmpty()) {
            throw new ValidationException("Subject is required.");
        }
        if (q.getMessage() == null || q.getMessage().trim().isEmpty()) {
            throw new ValidationException("Message is required.");
        }
        return queryRepository.createQuery(q);
    }
}
