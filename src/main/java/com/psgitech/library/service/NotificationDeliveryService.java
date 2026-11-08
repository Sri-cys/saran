package com.psgitech.library.service;

import com.psgitech.library.model.NotificationOutboxRecord;
import com.psgitech.library.repository.NotificationOutboxRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Worker service responsible for polling, locking, and dispatching outbox emails
 * via MailService with exponential backoff and terminal state handling.
 */
public class NotificationDeliveryService {
    private static final Logger LOGGER = Logger.getLogger(NotificationDeliveryService.class.getName());

    private static final int DEFAULT_BATCH_SIZE = 25;
    private static final int LEASE_SECONDS = 120;
    private static final int MAX_ATTEMPTS = 5;

    private static final int[] BACKOFF_SECONDS = { 60, 300, 900, 3600, 14400 };

    private final NotificationOutboxRepository outboxRepo;
    private final NotificationTemplateService templateService;

    public NotificationDeliveryService() {
        this.outboxRepo = new NotificationOutboxRepository();
        this.templateService = new NotificationTemplateService();
    }

    public NotificationDeliveryService(NotificationOutboxRepository outboxRepo) {
        this.outboxRepo = outboxRepo;
        this.templateService = new NotificationTemplateService();
    }

    public int deliverPendingBatch() {
        return deliverPendingBatch(DEFAULT_BATCH_SIZE);
    }

    public int deliverPendingBatch(int batchSize) {
        outboxRepo.recoverStaleLeases(LEASE_SECONDS);

        String workerToken = "worker-" + UUID.randomUUID().toString().substring(0, 8);
        List<NotificationOutboxRecord> batch = outboxRepo.claimBatch(batchSize, workerToken, LEASE_SECONDS);

        if (batch.isEmpty()) {
            return 0;
        }

        MailService mailService = MailServiceFactory.getMailService();
        if (mailService == null || mailService instanceof DisabledMailService) {
            LOGGER.fine("MailService is disabled; skipping outbox dispatch");
            return 0;
        }

        int deliveredCount = 0;
        for (NotificationOutboxRecord record : batch) {
            try {
                String toEmail = record.getRecipientEmail();
                if (toEmail == null || toEmail.trim().isEmpty()) {
                    outboxRepo.recordFailure(record.getOutboxId(), workerToken, record.getAttemptCount(), "MISSING_EMAIL", 3600, true);
                    continue;
                }

                String subject = record.getSubject();
                String body = record.getBodyHtml();
                if (subject == null || body == null) {
                    Map<String, Object> p = new HashMap<>();
                    if (record.getTitle() != null) p.put("bookTitle", record.getTitle());
                    NotificationTemplateService.RenderedTemplate rt = templateService.render(record.getNotificationType(), p);
                    subject = rt.getEmailSubject();
                    body = rt.getEmailBody();
                }

                mailService.sendHtml(toEmail, subject, body);
                outboxRepo.markSent(record.getOutboxId(), workerToken);
                deliveredCount++;
            } catch (Exception e) {
                int nextAttempt = record.getAttemptCount() + 1;
                boolean permanentlyFailed = nextAttempt >= MAX_ATTEMPTS;
                int backoffSeconds = calculateBackoff(nextAttempt);

                LOGGER.log(Level.WARNING, "Failed to deliver email to " + record.getRecipientEmail() +
                        " (attempt " + nextAttempt + "/" + MAX_ATTEMPTS + "): " + e.getMessage());

                outboxRepo.recordFailure(record.getOutboxId(), workerToken, record.getAttemptCount(),
                        e.getMessage(), backoffSeconds, permanentlyFailed);
            }
        }

        return deliveredCount;
    }

    public static int calculateBackoff(int attempt) {
        if (attempt <= 0) return BACKOFF_SECONDS[0];
        int idx = Math.min(attempt - 1, BACKOFF_SECONDS.length - 1);
        return BACKOFF_SECONDS[idx];
    }
}
