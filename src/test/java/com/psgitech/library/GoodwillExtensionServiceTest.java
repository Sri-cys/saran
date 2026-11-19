package com.psgitech.library;

import com.psgitech.library.enums.BorrowStatus;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.model.GoodwillExtension;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.repository.GoodwillExtensionRepository;
import com.psgitech.library.service.ActivityLogService;
import com.psgitech.library.service.GoodwillExtensionService;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class GoodwillExtensionServiceTest {

    private GoodwillExtensionService service;
    private MockGoodwillRepo mockRepo;
    private MockBorrowRepo mockBorrowRepo;

    static class MockGoodwillRepo extends GoodwillExtensionRepository {
        Map<Integer, GoodwillExtension> store = new HashMap<>();

        @Override
        public boolean hasUsedGoodwillExtension(int studentId) {
            return store.containsKey(studentId);
        }

        @Override
        public GoodwillExtension findByStudentId(int studentId) {
            return store.get(studentId);
        }

        @Override
        public void insert(GoodwillExtension ext, Connection conn) {
            if (store.containsKey(ext.getStudentId())) {
                throw new IllegalStateException("Duplicate entry: Student has already used one-time goodwill extension.");
            }
            ext.setId(store.size() + 1);
            store.put(ext.getStudentId(), ext);
        }
    }

    static class MockBorrowRepo extends BorrowRepository {
        Map<Integer, BorrowTransaction> txStore = new HashMap<>();

        @Override
        public BorrowTransaction findById(int transactionId) {
            return txStore.get(transactionId);
        }
    }

    static class MockActivityLogService extends ActivityLogService {
        @Override
        public void logEvent(String eventType, int actorId, String actorRole, String targetEntity, String targetId, String details, String ipAddress) {
            // no-op for tests
        }
    }

    @Before
    public void setUp() {
        mockRepo = new MockGoodwillRepo();
        mockBorrowRepo = new MockBorrowRepo();
        service = new GoodwillExtensionService(mockRepo, mockBorrowRepo, new MockActivityLogService());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBlankReasonThrowsException() {
        service.grantGoodwillExtension(1, 101, 7, "   ", 2, "127.0.0.1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTransactionNotFoundThrowsException() {
        service.grantGoodwillExtension(1, 999, 7, "Medical reasons", 2, "127.0.0.1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTransactionNotBelongingToStudentThrowsException() {
        BorrowTransaction tx = new BorrowTransaction();
        tx.setTransactionId(101);
        tx.setStudentId(2); // belongs to student 2
        tx.setStatus(BorrowStatus.ACTIVE);
        mockBorrowRepo.txStore.put(101, tx);

        // Student 1 tries to extend student 2's transaction
        service.grantGoodwillExtension(1, 101, 7, "Exam preparation", 2, "127.0.0.1");
    }

    @Test(expected = IllegalStateException.class)
    public void testNonActiveTransactionCannotBeExtended() {
        BorrowTransaction tx = new BorrowTransaction();
        tx.setTransactionId(102);
        tx.setStudentId(1);
        tx.setStatus(BorrowStatus.RETURNED);
        mockBorrowRepo.txStore.put(102, tx);

        service.grantGoodwillExtension(1, 102, 7, "Conference attendance", 2, "127.0.0.1");
    }

    @Test
    public void testOneTimeEnforcementBlocksSecondExtension() {
        // First loan
        BorrowTransaction tx1 = new BorrowTransaction();
        tx1.setTransactionId(201);
        tx1.setStudentId(5);
        tx1.setStatus(BorrowStatus.ACTIVE);
        tx1.setDueDate(LocalDate.now().plusDays(2));
        mockBorrowRepo.txStore.put(201, tx1);

        // Pre-condition: student has not used extension
        assertFalse(service.hasUsedGoodwillExtension(5));

        // Grant first extension in mock store
        GoodwillExtension ext1 = new GoodwillExtension(5, 201, 7, "First extension justification",
                tx1.getDueDate(), tx1.getDueDate().plusDays(7), 2);
        mockRepo.store.put(5, ext1);

        // Now hasUsed must be true
        assertTrue(service.hasUsedGoodwillExtension(5));

        // Second loan for same student
        BorrowTransaction tx2 = new BorrowTransaction();
        tx2.setTransactionId(202);
        tx2.setStudentId(5);
        tx2.setStatus(BorrowStatus.ACTIVE);
        tx2.setDueDate(LocalDate.now().plusDays(5));
        mockBorrowRepo.txStore.put(202, tx2);

        // Attempt second extension -> must throw IllegalStateException
        try {
            service.grantGoodwillExtension(5, 202, 7, "Second extension request", 2, "127.0.0.1");
            fail("Expected IllegalStateException for duplicate goodwill extension on same student");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("already been granted"));
        }
    }
}
