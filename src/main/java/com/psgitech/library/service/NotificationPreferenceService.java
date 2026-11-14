package com.psgitech.library.service;

import com.psgitech.library.config.NotificationPolicy;
import com.psgitech.library.enums.NotificationCategory;
import com.psgitech.library.model.NotificationPreference;
import com.psgitech.library.repository.NotificationPreferenceRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service managing user notification preferences and enforcing institutional policy locks.
 */
public class NotificationPreferenceService {
    private final NotificationPreferenceRepository preferenceRepo;

    public NotificationPreferenceService() {
        this.preferenceRepo = new NotificationPreferenceRepository();
    }

    public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepo) {
        this.preferenceRepo = preferenceRepo;
    }

    public List<NotificationPreference> getUserPreferences(int userId) {
        Map<NotificationCategory, NotificationPreference> stored = preferenceRepo.findByUserId(userId);
        List<NotificationPreference> fullList = new ArrayList<>();

        for (NotificationCategory cat : NotificationCategory.values()) {
            NotificationPreference pref = stored.get(cat);
            if (pref == null) {
                pref = new NotificationPreference(
                        userId,
                        cat,
                        NotificationPolicy.getDefaultInApp(cat),
                        NotificationPolicy.getDefaultEmail(cat)
                );
            }
            // Enforce locked state metadata
            boolean emailLocked = NotificationPolicy.isEmailLocked(cat);
            boolean inAppLocked = NotificationPolicy.isInAppLocked(cat);
            pref.setLocked(emailLocked || inAppLocked);
            pref.setLockReason(NotificationPolicy.getLockReason(cat));

            if (emailLocked) {
                pref.setEmailEnabled(true);
            }
            if (inAppLocked) {
                pref.setInAppEnabled(true);
            }

            fullList.add(pref);
        }
        return fullList;
    }

    public void updateUserPreferences(int userId, List<NotificationPreference> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }

        List<NotificationPreference> sanitized = new ArrayList<>();
        for (NotificationPreference p : updates) {
            if (p.getCategory() == null) continue;

            NotificationCategory cat = p.getCategory();
            boolean emailLocked = NotificationPolicy.isEmailLocked(cat);
            boolean inAppLocked = NotificationPolicy.isInAppLocked(cat);

            boolean emailVal = emailLocked || p.isEmailEnabled();
            boolean inAppVal = inAppLocked || p.isInAppEnabled();

            NotificationPreference clean = new NotificationPreference(userId, cat, inAppVal, emailVal);
            sanitized.add(clean);
        }

        preferenceRepo.upsertBatch(userId, sanitized);
    }
}

