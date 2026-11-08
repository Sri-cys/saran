package com.psgitech.library.scheduler;

import com.psgitech.library.service.NotificationDeliveryService;
import com.psgitech.library.service.NotificationReminderService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Unified Background Worker (Decision D7).
 * Single daemon executor executing outbox email dispatches every 30 seconds
 * and periodic loan reminder scans every 15 minutes.
 */
public class NotificationWorker {
    private static final Logger LOGGER = Logger.getLogger(NotificationWorker.class.getName());

    private static final int POLL_INTERVAL_SECONDS = 30;
    private static final int REMINDER_INTERVAL_TICKS = 30; // 30 * 30s = 15 minutes

    private static final NotificationWorker INSTANCE = new NotificationWorker();

    private final NotificationDeliveryService deliveryService;
    private final NotificationReminderService reminderService;
    private final AtomicInteger tickCounter = new AtomicInteger(0);

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;
    private volatile boolean running = false;

    private NotificationWorker() {
        this.deliveryService = new NotificationDeliveryService();
        this.reminderService = new NotificationReminderService();
    }

    public static NotificationWorker getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (running) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PsgItech-NotificationWorker");
            t.setDaemon(true);
            return t;
        });

        scheduledTask = scheduler.scheduleWithFixedDelay(this::runWorkerIteration, 10, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
        running = true;
        LOGGER.info("PsgItech-NotificationWorker started (interval: " + POLL_INTERVAL_SECONDS + "s)");
    }

    public void runWorkerIteration() {
        try {
            // 1. Process pending email outbox
            deliveryService.deliverPendingBatch();

            // 2. Process loan reminder scans periodically (every 15 min)
            int tick = tickCounter.incrementAndGet();
            if (tick >= REMINDER_INTERVAL_TICKS) {
                tickCounter.set(0);
                reminderService.processAllReminders();
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Error in notification worker iteration", t);
        }
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        LOGGER.info("PsgItech-NotificationWorker stopped cleanly");
    }

    public boolean isRunning() {
        return running;
    }
}
