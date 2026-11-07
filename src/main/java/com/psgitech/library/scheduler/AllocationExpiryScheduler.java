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
        MailService mailService = MailServiceFactory.getMailService();

        for (AllocationRecord alloc : needingReminder) {
            try {
                String toEmail = alloc.getStudentEmail();
                if (toEmail != null && !toEmail.trim().isEmpty()) {
                    String subject = "PSG iTech Library - Action Required: Book Hold Expires in Less Than 6 Hours";
                    String body = "Dear " + alloc.getStudentName() + ",\n\n" +
                            "This is a reminder that your reserved book hold at PSG iTech Library is nearing its deadline:\n\n" +
                            "Book Title: " + alloc.getBookTitle() + "\n" +
                            "Physical Copy: " + alloc.getCopyCode() + "\n" +
                            "Collection Deadline: " + alloc.getExpiresAt() + "\n\n" +
                            "Please collect your book at the circulation desk before the deadline. " +
                            "Uncollected books will be automatically released to the next student in the reservation queue.\n\n" +
                            "Regards,\nPSG iTech Library Circulation Desk";

                    mailService.send(toEmail, subject, body);
                }
                allocationRepository.markReminderSent(alloc.getAllocationId());
                count++;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to send reminder for allocation #" + alloc.getAllocationId(), e);
            }
        }
        return count;
    }
}
