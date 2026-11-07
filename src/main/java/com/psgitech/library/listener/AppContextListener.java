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
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        LOGGER.info("PSG iTech Library System shutting down...");
        TransactionRetryHelper.removeListener(ConcurrencyManager.getInstance().getMetrics());
    }
}

