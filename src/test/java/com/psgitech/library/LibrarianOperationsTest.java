package com.psgitech.library;

import com.psgitech.library.exception.ValidationException;
import com.psgitech.library.model.AcquisitionRequest;
import com.psgitech.library.model.Book;
import com.psgitech.library.model.BookPurchase;
import com.psgitech.library.model.LibraryQuery;
import com.psgitech.library.repository.AcquisitionRepository;
import com.psgitech.library.repository.BookRepository;
import com.psgitech.library.service.AcquisitionService;
import com.psgitech.library.service.CsvImportService;
import com.psgitech.library.service.PurchaseService;
import com.psgitech.library.service.QueryService;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class LibrarianOperationsTest {

    private AcquisitionService acquisitionService;
    private AcquisitionRepository acquisitionRepository;
    private CsvImportService csvImportService;
    private QueryService queryService;
    private PurchaseService purchaseService;
    private BookRepository bookRepository;

    @Before
    public void setUp() {
        this.acquisitionService = new AcquisitionService();
        this.acquisitionRepository = new AcquisitionRepository();
        this.csvImportService = new CsvImportService();
        this.queryService = new QueryService();
        this.purchaseService = new PurchaseService();
        this.bookRepository = new BookRepository();
    }

    @Test
    public void testAcquisitionRequestLifecycle() {
        AcquisitionRequest req = new AcquisitionRequest();
        req.setTitle("Quantum Computing Concepts");
        req.setAuthor("Dr. A. Scientist");
        req.setIsbn("9780123456789");
        req.setDepartment("CSE");
        req.setRequesterId(1);
        req.setEstimatedPrice(new BigDecimal("750.00"));
        req.setRemarks("Recommended for advanced research");

        int reqId = acquisitionRepository.createRequest(req);
        assertTrue(reqId > 0);

        AcquisitionRequest loaded = acquisitionService.getById(reqId);
        assertNotNull(loaded);
        assertEquals("REQUESTED", loaded.getStatus());

        // 1. Approve
        boolean approved = acquisitionService.updateStatus(reqId, "APPROVED", "Approved for purchase", 1);
        assertTrue(approved);
        assertEquals("APPROVED", acquisitionService.getById(reqId).getStatus());

        // 2. Mark Ordered
        boolean ordered = acquisitionService.updateStatus(reqId, "ORDERED", "PO-9912 placed", 1);
        assertTrue(ordered);
        assertEquals("ORDERED", acquisitionService.getById(reqId).getStatus());
    }

    @Test(expected = ValidationException.class)
    public void testAcquisitionInvalidTransitionRejected() {
        AcquisitionRequest req = new AcquisitionRequest();
        req.setTitle("Discrete Mathematics");
        req.setAuthor("Rosen");
        req.setIsbn("9780073383095");
        req.setDepartment("CSE");
        req.setRequesterId(1);

        int reqId = acquisitionRepository.createRequest(req);
        assertTrue(reqId > 0);

        // Direct transition from REQUESTED to RECEIVED is invalid
        acquisitionService.updateStatus(reqId, "RECEIVED", "Invalid jump", 1);
    }

    @Test
    public void testAcquisitionConvertToInventory() {
        AcquisitionRequest req = new AcquisitionRequest();
        String uniqueIsbn = String.format("978%010d", Math.abs(System.currentTimeMillis() % 10000000000L));
        req.setTitle("Compiler Design Principles");
        req.setAuthor("Aho and Ullman");
        req.setIsbn(uniqueIsbn);
        req.setDepartment("CSE");
        req.setRequesterId(1);
        req.setEstimatedPrice(new BigDecimal("600.00"));

        int reqId = acquisitionRepository.createRequest(req);
        assertTrue(reqId > 0);

        acquisitionService.updateStatus(reqId, "APPROVED", "Approved", 1);
        acquisitionService.updateStatus(reqId, "ORDERED", "Ordered", 1);

        Book book = acquisitionService.convertToInventory(reqId, "Computer Science", 2, "2", "East", "B", "R2", "S4", 1);
        assertNotNull(book);
        assertTrue(book.getBookId() > 0);
        assertEquals("RECEIVED", acquisitionService.getById(reqId).getStatus());
    }

    @Test
    public void testCsvPreviewAndValidation() throws IOException {
        String csvContent = "Title,Author,ISBN,Publisher,Edition,Category,Year,Copies,Floor,Aisle,Shelf\r\n" +
                "\"Modern Databases\",\"Silberschatz\",\"9780072958867\",\"McGraw-Hill\",\"6th\",\"CSE\",2020,3,\"1\",\"A2\",\"S1\"\r\n" +
                "\"\",\"Missing Author\",\"9780000000000\",\"Pub\",\"1st\",\"CSE\",2020,1,\"1\",\"A1\",\"S1\"\r\n";

        List<CsvImportService.CsvRowPreview> previews = csvImportService.previewCsv(csvContent);
        assertEquals(2, previews.size());

        assertEquals("VALID", previews.get(0).status);
        assertEquals("Modern Databases", previews.get(0).title);
        assertEquals(3, previews.get(0).copies);

        assertEquals("INVALID", previews.get(1).status);
        assertTrue(previews.get(1).errorMessage.contains("Title is required"));
    }

    @Test
    public void testCsvCommitBatchWithSkipDuplicates() throws IOException {
        String uniqueIsbn1 = String.format("9781%09d", Math.abs(System.currentTimeMillis() % 1000000000L));
        String uniqueIsbn2 = String.format("9782%09d", Math.abs((System.currentTimeMillis() + 1) % 1000000000L));

        String csvContent = "Title,Author,ISBN,Publisher,Edition,Category,Year,Copies,Floor,Aisle,Shelf\r\n" +
                "\"Distributed Systems\",\"Tanenbaum\",\"" + uniqueIsbn1 + "\",\"Pearson\",\"3rd\",\"CSE\",2021,2,\"1\",\"A1\",\"S1\"\r\n" +
                "\"Cloud Computing Principles\",\"Rajkumar\",\"" + uniqueIsbn2 + "\",\"Wiley\",\"1st\",\"CSE\",2022,1,\"1\",\"A1\",\"S2\"\r\n";

        List<CsvImportService.CsvRowPreview> previews = csvImportService.previewCsv(csvContent);
        Map<String, Object> result = csvImportService.commitBatch(previews, true, 1);

        assertNotNull(result);
        assertEquals(2, result.get("importedCount"));
        assertEquals(0, result.get("skippedCount"));
    }

    @Test
    public void testStudentQueryReply() {
        LibraryQuery q = new LibraryQuery();
        q.setStudentId(1);
        q.setCategory("General");
        q.setSubject("Library Weekend Timings");
        q.setMessage("What are the central library working hours on Sundays?");

        int queryId = queryService.submitQuery(q);
        assertTrue(queryId > 0);

        LibraryQuery loaded = queryService.getById(queryId);
        assertNotNull(loaded);
        assertEquals("OPEN", loaded.getStatus());

        boolean replied = queryService.replyQuery(queryId, "Sunday hours are 9:00 AM to 5:00 PM.", 1);
        assertTrue(replied);

        LibraryQuery resolved = queryService.getById(queryId);
        assertEquals("RESOLVED", resolved.getStatus());
        assertEquals("Sunday hours are 9:00 AM to 5:00 PM.", resolved.getResponseText());
    }

    @Test
    public void testPurchaseRecordCreationAndTotalCalculation() {
        BookPurchase p = new BookPurchase();
        String uniqueInvoice = "INV-" + System.currentTimeMillis();
        p.setSupplierName("Higginbothams Coimbatore");
        p.setInvoiceNo(uniqueInvoice);
        p.setPurchaseDate(Date.valueOf(LocalDate.now()));
        p.setUnitPrice(new BigDecimal("450.00"));
        p.setQuantity(4);
        p.setRemarks("Departmental library addition");

        int purchaseId = purchaseService.recordPurchase(p, 1);
        assertTrue(purchaseId > 0);
        assertEquals(new BigDecimal("1800.00"), p.getTotalAmount()); // 450.00 * 4

        // Duplicate invoice for same supplier should be rejected
        BookPurchase duplicate = new BookPurchase();
        duplicate.setSupplierName("Higginbothams Coimbatore");
        duplicate.setInvoiceNo(uniqueInvoice);
        duplicate.setPurchaseDate(Date.valueOf(LocalDate.now()));
        duplicate.setUnitPrice(new BigDecimal("100.00"));
        duplicate.setQuantity(1);

        try {
            purchaseService.recordPurchase(duplicate, 1);
            fail("Expected ValidationException on duplicate invoice number");
        } catch (ValidationException e) {
            assertTrue(e.getMessage().contains("already exists"));
        }
    }
}
