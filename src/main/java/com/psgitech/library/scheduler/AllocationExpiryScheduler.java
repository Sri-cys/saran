package com.psgitech.library.scheduler;

import com.psgitech.library.model.AllocationRecord;
import com.psgitech.library.repository.AllocationRepository;
import com.psgitech.library.service.AllocationService;
import com.psgitech.library.service.MailService;
import com.psgitech.library.service.MailServiceFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Background daemon scheduler managing hold-shelf allocation expirations and 6-hour deadline reminders.
 * Runs on a dedicated daemon thread "PsgItech-AllocationExpiry" at fixed intervals (default 60 seconds).
 */
public class AllocationExpiryScheduler {
    private static final Logger LOGGER = Logger.getLogger(AllocationExpiryScheduler.class.getName());

    private static AllocationExpiryScheduler instance;

    private final ScheduledExecutorService executor;
    private final AllocationService allocationService;
    private final AllocationRepository allocationRepository;
    private final int sweepIntervalSeconds;
    private final boolean schedulerEnabled;

    public AllocationExpiryScheduler() {
        this.allocationService = new AllocationService();
        this.allocationRepository = new AllocationRepository();

        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) props.load(in);
        } catch (Exception ignored) {}

        this.sweepIntervalSeconds = Integer.parseInt(System.getProperty(
                "allocation.expiry.sweep.interval.seconds",
                props.getProperty("allocation.expiry.sweep.interval.seconds", "60")
        ));
        this.schedulerEnabled = Boolean.parseBoolean(System.getProperty(
                "allocation.expiry.scheduler.enabled",
                props.getProperty("allocation.expiry.scheduler.enabled", "true")
        ));

        this.executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "PsgItech-AllocationExpiry");
                t.setDaemon(true);
                return t;
            }
        });
    }

    public static synchronized AllocationExpiryScheduler getInstance() {
        if (instance == null) {
            instance = new AllocationExpiryScheduler();
        }
        return instance;
    }

    public synchronized void start() {
        if (!schedulerEnabled) {
            LOGGER.info("AllocationExpiryScheduler is disabled by configuration.");
            return;
        }

        LOGGER.info("Starting AllocationExpiryScheduler (sweep interval: " + sweepIntervalSeconds + "s)...");
        executor.scheduleWithFixedDelay(this::runSweepTask, 10, sweepIntervalSeconds, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        LOGGER.info("Stopping AllocationExpiryScheduler...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void runSweepTask() {
        try {
            sweepExpiredAllocations();
            sendUpcomingDeadlineReminders();
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Unexpected error in AllocationExpiryScheduler task", t);
        }
    }

    public int sweepExpiredAllocations() {
        List<Integer> expiredIds = allocationRepository.findExpiredAllocationIds();
        int count = 0;
        for (Integer id : expiredIds) {
            try {
                allocationService.expireAndReallocate(id);
                count++;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to expire allocation #" + id, e);
            }
        }
        if (count > 0) {
            LOGGER.info("AllocationExpiryScheduler expired and processed " + count + " hold allocation(s).");
        }
        return count;
    }

    public int sendUpcomingDeadlineReminders() {
        List<AllocationRecord> needingReminder = allocationRepository.findAllocationsNeedingReminder();
        int count = 0;
        com.psgitech.library.service.NotificationRecorder recorder = new com.psgitech.library.service.NotificationRecorder();

        for (AllocationRecord alloc : needingReminder) {
            try (java.sql.Connection conn = com.psgitech.library.util.DBConnection.getConnection()) {
                conn.setAutoCommit(false);
                // Atomic claim update to ensure only one worker claims the reminder
                String claimSql = "UPDATE allocations SET reminder_sent_at = NOW(), reminder_status = 'SENT' " +
                        "WHERE allocation_id = ? AND reminder_sent_at IS NULL";
                int claimed = 0;
                try (java.sql.PreparedStatement ps = conn.prepareStatement(claimSql)) {
                    ps.setInt(1, alloc.getAllocationId());
                    claimed = ps.executeUpdate();
                }

                if (claimed > 0) {
                    com.psgitech.library.model.NotificationDraft draft = com.psgitech.library.model.NotificationDraft
                            .builder(com.psgitech.library.enums.NotificationType.ALLOCATION_REMINDER, alloc.getStudentId(), "ALLOC_REMINDER:" + alloc.getAllocationId())
                            .recipientEmail(alloc.getStudentEmail())
                            .entity("ALLOCATION", alloc.getAllocationId())
                            .param("bookTitle", alloc.getBookTitle())
                            .param("pickupDeadline", alloc.getExpiresAt() != null ? alloc.getExpiresAt().toString() : "")
                            .build();

                    recorder.record(conn, draft);
                    conn.commit();
                    count++;
                } else {
                    conn.rollback();
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to send reminder for allocation #" + alloc.getAllocationId(), e);
            }
        }
        return count;
    }
}

