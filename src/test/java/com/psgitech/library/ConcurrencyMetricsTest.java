package com.psgitech.library;

import com.psgitech.library.concurrency.ConcurrencyLimitExceededException;
import com.psgitech.library.concurrency.ConcurrencyMetrics;
import com.psgitech.library.concurrency.ConcurrencyOperation;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ConcurrencyMetricsTest {

    @Test
    public void testConcurrentMetricsUpdatesLoseZeroIncrements() throws Exception {
        ConcurrencyMetrics metrics = new ConcurrencyMetrics();
        int threads = 20;
        int incrementsPerThread = 500;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            exec.submit(() -> {
                try {
                    start.await();
                    for (int j = 0; j < incrementsPerThread; j++) {
                        metrics.recordAdmitted(ConcurrencyOperation.BOOK_ISSUE, 5L);
                        metrics.recordCompleted(ConcurrencyOperation.BOOK_ISSUE, 200, 20L);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        exec.shutdownNow();

        long expected = (long) threads * incrementsPerThread;
        assertEquals(expected, metrics.getTotalAdmitted());
        assertEquals(expected, metrics.getTotalCompleted());

        ConcurrencyMetrics.OperationStats stats = metrics.getOperationStatsMap().get(ConcurrencyOperation.BOOK_ISSUE);
        assertNotNull(stats);
        assertEquals(expected, stats.getCompleted());
        assertEquals(0, stats.getActive());
    }

    @Test
    public void testRejectionsAndDeadlockHooks() {
        ConcurrencyMetrics metrics = new ConcurrencyMetrics();

        metrics.recordRejection(ConcurrencyLimitExceededException.Reason.WAIT_TIMEOUT);
        metrics.recordRejection(ConcurrencyLimitExceededException.Reason.WAITING_ROOM_FULL);
        metrics.recordRejection(ConcurrencyLimitExceededException.Reason.USER_INFLIGHT_CAP);
        metrics.recordRejection(ConcurrencyLimitExceededException.Reason.POOL_EXHAUSTED);
        metrics.recordInterrupted();

        assertEquals(1, metrics.getRejectionsWaitTimeout());
        assertEquals(1, metrics.getRejectionsWaitingRoomFull());
        assertEquals(1, metrics.getRejectionsUserCap());
        assertEquals(1, metrics.getRejectionsPoolExhausted());
        assertEquals(1, metrics.getRejectionsInterrupted());

        // Test TransactionRetryListener hooks
        metrics.onDeadlockRetry("testIssue", 1, 25L);
        metrics.onDeadlockRetry("testIssue", 2, 50L);
        metrics.onLockWaitTimeout("testReturn", 1);

        assertEquals(2, metrics.getDeadlockRetries());
        assertEquals(1, metrics.getLockWaitTimeouts());
    }

    @Test
    public void testHistogramPercentilesAndRecentRejections() {
        ConcurrencyMetrics metrics = new ConcurrencyMetrics();

        // Record various durations across different buckets
        metrics.recordCompleted(ConcurrencyOperation.BOOK_ISSUE, 200, 3L);    // <= 5ms
        metrics.recordCompleted(ConcurrencyOperation.BOOK_ISSUE, 200, 8L);    // <= 10ms
        metrics.recordCompleted(ConcurrencyOperation.BOOK_ISSUE, 200, 18L);   // <= 25ms
        metrics.recordCompleted(ConcurrencyOperation.BOOK_ISSUE, 200, 35L);   // <= 50ms
        metrics.recordCompleted(ConcurrencyOperation.BOOK_ISSUE, 200, 75L);   // <= 100ms
        metrics.recordCompleted(ConcurrencyOperation.BOOK_ISSUE, 200, 150L);  // <= 250ms
        metrics.recordCompleted(ConcurrencyOperation.BOOK_ISSUE, 200, 350L);  // <= 500ms
        metrics.recordCompleted(ConcurrencyOperation.BOOK_ISSUE, 200, 800L);  // <= 1000ms
        metrics.recordCompleted(ConcurrencyOperation.BOOK_ISSUE, 200, 1500L); // <= 2500ms
        metrics.recordCompleted(ConcurrencyOperation.BOOK_ISSUE, 200, 3200L); // <= 5000ms
        metrics.recordCompleted(ConcurrencyOperation.BOOK_ISSUE, 200, 6000L); // > 5000ms

        assertEquals(6000L, metrics.getMaxProcessingMs());

        java.util.Map<String, Long> hist = metrics.getLatencyHistogram();
        assertEquals(11, hist.size());
        assertEquals(Long.valueOf(1), hist.get("<=5ms"));
        assertEquals(Long.valueOf(1), hist.get("<=10ms"));
        assertEquals(Long.valueOf(1), hist.get(">5000ms"));

        assertTrue(metrics.getPercentileLatency(50) > 0);
        assertTrue(metrics.getPercentileLatency(99) >= metrics.getPercentileLatency(50));

        // Test ring buffer
        for (int i = 0; i < 60; i++) {
            metrics.recordRejection(ConcurrencyLimitExceededException.Reason.WAIT_TIMEOUT, "/test/path/" + i, ConcurrencyOperation.BOOK_ISSUE);
        }
        java.util.List<ConcurrencyMetrics.RejectionEvent> rejections = metrics.getRecentRejections();
        assertEquals(50, rejections.size()); // capped at 50
        // Newest entry is at index 0
        assertEquals(ConcurrencyLimitExceededException.Reason.WAIT_TIMEOUT.name(), rejections.get(0).getReason());
        assertEquals("/test/path/59", rejections.get(0).getPath());
    }
}

