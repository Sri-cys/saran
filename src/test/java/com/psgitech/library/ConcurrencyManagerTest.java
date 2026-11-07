package com.psgitech.library;

import com.psgitech.library.concurrency.AdmissionTicket;
import com.psgitech.library.concurrency.ConcurrencyConfig;
import com.psgitech.library.concurrency.ConcurrencyLimitExceededException;
import com.psgitech.library.concurrency.ConcurrencyManager;
import com.psgitech.library.concurrency.ConcurrencyOperation;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ConcurrencyManagerTest {

    private ConcurrencyManager manager;

    @After
    public void tearDown() {
        if (manager != null) {
            // Verify quiescence
            assertEquals("Active requests must return to 0 at quiescence", 0, manager.getActiveRequests());
            assertEquals("Waiting requests must return to 0 at quiescence", 0, manager.getWaitingRequests());
            assertEquals("Permits must be fully restored",
                    manager.getConfig().getMaxConcurrentMutations(), manager.getAvailablePermits());
        }
    }

    @Test
    public void testTicketDoubleCloseIsIdempotent() {
        ConcurrencyConfig config = new ConcurrencyConfig(true, 5, 10, 1000L, 3, true);
        manager = new ConcurrencyManager(config);

        AdmissionTicket ticket = manager.acquireTicket(ConcurrencyOperation.BOOK_ISSUE, 101);
        assertEquals(1, manager.getActiveRequests());
        assertEquals(4, manager.getAvailablePermits());

        // Close once
        ticket.close();
        assertEquals(0, manager.getActiveRequests());
        assertEquals(5, manager.getAvailablePermits());

        // Close second time: should do nothing (no duplicate permit release)
        ticket.close();
        assertEquals(0, manager.getActiveRequests());
        assertEquals(5, manager.getAvailablePermits());
    }

    @Test
    public void testPerUserInflightCap() {
        ConcurrencyConfig config = new ConcurrencyConfig(true, 10, 20, 1000L, 2, true); // cap = 2
        manager = new ConcurrencyManager(config);

        int userId = 42;
        AdmissionTicket t1 = manager.acquireTicket(ConcurrencyOperation.BOOK_ISSUE, userId);
        AdmissionTicket t2 = manager.acquireTicket(ConcurrencyOperation.BOOK_ISSUE, userId);

        assertEquals(2, manager.getActiveRequests());
        assertEquals(1, manager.getTrackedUsersCount());

        // 3rd concurrent request from same user should throw USER_INFLIGHT_CAP
        try {
            manager.acquireTicket(ConcurrencyOperation.BOOK_ISSUE, userId);
            fail("Expected ConcurrencyLimitExceededException due to user cap");
        } catch (ConcurrencyLimitExceededException e) {
            assertEquals(ConcurrencyLimitExceededException.Reason.USER_INFLIGHT_CAP, e.getReason());
            assertEquals(429, e.getHttpStatus());
        }

        // Different user can still acquire
        AdmissionTicket tOther = manager.acquireTicket(ConcurrencyOperation.BOOK_ISSUE, 99);
        assertEquals(3, manager.getActiveRequests());

        t1.close();
        t2.close();
        tOther.close();

        // Tracked user map cleans up when active counts hit 0
        assertEquals(0, manager.getTrackedUsersCount());
    }

    @Test
    public void testWaitingRoomFullFastRejection() throws Exception {
        // Small capacity 1, waiting room 2
        ConcurrencyConfig config = new ConcurrencyConfig(true, 1, 2, 5000L, 5, true);
        manager = new ConcurrencyManager(config);

        AdmissionTicket t1 = manager.acquireTicket(ConcurrencyOperation.BOOK_ISSUE, 1);

        CountDownLatch waiter1Started = new CountDownLatch(1);
        CountDownLatch waiter2Started = new CountDownLatch(1);
        CountDownLatch releaseWaiters = new CountDownLatch(1);

        Thread w1 = new Thread(() -> {
            waiter1Started.countDown();
            try (AdmissionTicket t = manager.acquireTicket(ConcurrencyOperation.BOOK_ISSUE, 2)) {
                releaseWaiters.await();
            } catch (Exception ignored) {}
        });

        Thread w2 = new Thread(() -> {
            waiter2Started.countDown();
            try (AdmissionTicket t = manager.acquireTicket(ConcurrencyOperation.BOOK_ISSUE, 3)) {
                releaseWaiters.await();
            } catch (Exception ignored) {}
        });

        w1.start();
        waiter1Started.await();
        Thread.sleep(50);

        w2.start();
        waiter2Started.await();
        Thread.sleep(50);

        // Waiting room is now full (2 waiting). A 3rd waiter must be rejected IMMEDIATELY with WAITING_ROOM_FULL
        try {
            manager.acquireTicket(ConcurrencyOperation.BOOK_ISSUE, 4);
            fail("Expected WAITING_ROOM_FULL rejection");
        } catch (ConcurrencyLimitExceededException e) {
            assertEquals(ConcurrencyLimitExceededException.Reason.WAITING_ROOM_FULL, e.getReason());
            assertEquals(503, e.getHttpStatus());
        }

        // Release all
        t1.close();
        releaseWaiters.countDown();
        w1.join();
        w2.join();
    }

    @Test
    public void testWaitTimeoutRejection() {
        // Capacity 1, timeout 100ms
        ConcurrencyConfig config = new ConcurrencyConfig(true, 1, 5, 100L, 5, true);
        manager = new ConcurrencyManager(config);

        AdmissionTicket holder = manager.acquireTicket(ConcurrencyOperation.BOOK_ISSUE, 1);

        try {
            manager.acquireTicket(ConcurrencyOperation.BOOK_ISSUE, 2);
            fail("Expected WAIT_TIMEOUT");
        } catch (ConcurrencyLimitExceededException e) {
            assertEquals(ConcurrencyLimitExceededException.Reason.WAIT_TIMEOUT, e.getReason());
            assertEquals(503, e.getHttpStatus());
            assertEquals(2, e.getRetryAfterSeconds());
        } finally {
            holder.close();
        }
    }

    @Test
    public void testFairFifoAdmissionOrder() throws Exception {
        // Capacity 1, fair queue
        ConcurrencyConfig config = new ConcurrencyConfig(true, 1, 10, 5000L, 5, true);
        manager = new ConcurrencyManager(config);

        AdmissionTicket blocker = manager.acquireTicket(ConcurrencyOperation.BOOK_ISSUE, 0);

        List<Integer> admissionOrder = Collections.synchronizedList(new ArrayList<>());
        int waiterCount = 4;
        Thread[] threads = new Thread[waiterCount];

        for (int i = 0; i < waiterCount; i++) {
            final int id = i + 1;
            threads[i] = new Thread(() -> {
                try (AdmissionTicket t = manager.acquireTicket(ConcurrencyOperation.BOOK_ISSUE, id)) {
                    admissionOrder.add(id);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            threads[i].start();
            // Ensure thread has lined up in FIFO order before starting the next
            Thread.sleep(50);
        }

        // Release the blocking permit
        blocker.close();

        for (Thread t : threads) {
            t.join(3000);
        }

        // Assert strictly FIFO order: 1, 2, 3, 4
        assertEquals(waiterCount, admissionOrder.size());
        for (int i = 0; i < waiterCount; i++) {
            assertEquals(Integer.valueOf(i + 1), admissionOrder.get(i));
        }
    }

    @Test
    public void testActiveCountUnder500ThreadsNeverExceedsCapacity() throws Exception {
        final int capacity = 8;
        ConcurrencyConfig config = new ConcurrencyConfig(true, capacity, 1000, 2000L, 10, true);
        manager = new ConcurrencyManager(config);

        int totalThreads = 500;
        ExecutorService exec = Executors.newFixedThreadPool(50);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(totalThreads);
        AtomicInteger maxObservedActive = new AtomicInteger(0);

        for (int i = 0; i < totalThreads; i++) {
            final int userId = i + 1;
            exec.submit(() -> {
                try {
                    startGate.await();
                    try (AdmissionTicket t = manager.acquireTicket(ConcurrencyOperation.BOOK_ISSUE, userId)) {
                        int active = manager.getActiveRequests();
                        maxObservedActive.accumulateAndGet(active, Math::max);
                        Thread.sleep(2);
                    } catch (ConcurrencyLimitExceededException ignored) {
                        // Fast fails on timeout/overflow are expected and valid
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        doneGate.await(15, TimeUnit.SECONDS);
        exec.shutdownNow();

        assertTrue("Max observed active (" + maxObservedActive.get() + ") must never exceed capacity (" + capacity + ")",
                maxObservedActive.get() <= capacity);
    }
}

