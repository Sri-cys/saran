package com.psgitech.library.listener;

import com.psgitech.library.concurrency.ConcurrencyConfig;
import com.psgitech.library.concurrency.ConcurrencyManager;
import com.psgitech.library.util.TransactionRetryHelper;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.logging.Logger;

/**
 * Application lifecycle listener.
 * Initializes database connection pool and concurrency manager on startup,
 * registers retry listener hooks, and cleanly flushes resources on shutdown.
 */
public class AppContextListener implements ServletContextListener {
    private static final Logger LOGGER = Logger.getLogger(AppContextListener.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        LOGGER.info("PSG iTech Library System starting up...");

        // Initialize Concurrency Manager and Config
        ConcurrencyManager manager = ConcurrencyManager.getInstance();
        ConcurrencyConfig config = manager.getConfig();

        // Register ConcurrencyMetrics with TransactionRetryHelper
        TransactionRetryHelper.addListener(manager.getMetrics());

        LOGGER.info(String.format("Concurrency Manager initialized: Limiter=%s, MaxMutations=%d, MaxWaiting=%d, Timeout=%dms, UserCap=%d",
                config.isLimiterEnabled(),
                config.getMaxConcurrentMutations(),
                config.getMaxWaitingMutations(),
                config.getWaitTimeoutMs(),
                config.getUserInflightCap()));

        // Start Background Allocation Expiry & Reminder Scheduler
        com.psgitech.library.scheduler.AllocationExpiryScheduler.getInstance().start();

        // Start Unified Notification Worker (Feature 11 Decision D7)
        com.psgitech.library.scheduler.NotificationWorker.getInstance().start();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        LOGGER.info("PSG iTech Library System shutting down...");
        try {
            com.psgitech.library.scheduler.NotificationWorker.getInstance().stop();
        } catch (Throwable t) {
            LOGGER.warning("Error stopping NotificationWorker: " + t.getMessage());
        }

        try {
            com.psgitech.library.scheduler.AllocationExpiryScheduler.getInstance().stop();
        } catch (Throwable t) {
            LOGGER.warning("Error stopping AllocationExpiryScheduler: " + t.getMessage());
        }

        try {
            TransactionRetryHelper.removeListener(ConcurrencyManager.getInstance().getMetrics());
        } catch (Throwable t) {
            LOGGER.warning("Error removing retry listener: " + t.getMessage());
        }

        // Deregister JDBC drivers to prevent Tomcat memory leaks
        java.util.Enumeration<java.sql.Driver> drivers = java.sql.DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            java.sql.Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == getClass().getClassLoader()) {
                try {
                    java.sql.DriverManager.deregisterDriver(driver);
                    LOGGER.info("Deregistered JDBC driver: " + driver);
                } catch (Throwable t) {
                    LOGGER.warning("Error deregistering JDBC driver " + driver + ": " + t.getMessage());
                }
            }
        }

        // Stop MySQL AbandonedConnectionCleanupThread
        try {
            com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
            LOGGER.info("MySQL AbandonedConnectionCleanupThread stopped successfully.");
        } catch (Throwable t) {
            LOGGER.warning("Could not stop MySQL AbandonedConnectionCleanupThread: " + t.getMessage());
        }
    }
}

