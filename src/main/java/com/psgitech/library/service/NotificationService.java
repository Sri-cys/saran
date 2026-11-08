package com.psgitech.library.service;

import com.psgitech.library.enums.NotificationCategory;
import com.psgitech.library.model.Notification;
import com.psgitech.library.repository.NotificationRepository;

import java.util.List;

/**
 * Service orchestrating in-app notification querying and read-state management.
 */
public class NotificationService {
    private final NotificationRepository notificationRepo;

    public NotificationService() {
        this.notificationRepo = new NotificationRepository();
    }

    public NotificationService(NotificationRepository notificationRepo) {
        this.notificationRepo = notificationRepo;
    }

    public List<Notification> getNotifications(int userId, NotificationCategory category, Boolean unreadOnly, Long cursor, int limit) {
        return notificationRepo.findList(userId, category, unreadOnly, cursor, limit);
    }

    public int getUnreadCount(int userId) {
        return notificationRepo.countUnread(userId);
    }

    public Long getLatestNotificationId(int userId) {
        return notificationRepo.findLatestId(userId);
    }

    public boolean markAsRead(int userId, long notificationId) {
        if (notificationId <= 0) {
            return false;
        }
        return notificationRepo.markAsRead(userId, notificationId);
    }

    public int markAllAsRead(int userId, long upToId) {
        return notificationRepo.markAllAsRead(userId, upToId, 500);
    }
}
