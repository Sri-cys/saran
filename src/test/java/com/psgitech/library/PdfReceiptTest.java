package com.psgitech.library;

import com.psgitech.library.service.PdfReceiptService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class PdfReceiptTest {

    private PdfReceiptService pdfReceiptService;

    @Before
    public void setUp() {
        this.pdfReceiptService = new PdfReceiptService();
    }

    @Test
    public void testGenerateValidPdfReceipt() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("receiptNumber", "RCP-2026-TEST01");
        data.put("paymentDate", "2026-03-29 14:30:00");
        data.put("paymentStatus", "PAID");
        data.put("studentName", "Test Student");
        data.put("rollNumber", "715521104001");
        data.put("department", "CSE");
        data.put("email", "student@psgitech.ac.in");
        data.put("bookTitle", "Database System Concepts");
        data.put("bookAuthor", "Silberschatz");
        data.put("barcode", "PSG-BK1-C01");
        data.put("overdueDays", 5);
        data.put("perDayRate", new BigDecimal("2.00"));
        data.put("calculatedFine", new BigDecimal("10.00"));
        data.put("amountPaid", new BigDecimal("10.00"));
        data.put("paymentMethod", "UPI");
        data.put("transactionRef", "UPI-1234567890");

        byte[] pdfBytes = pdfReceiptService.generateReceiptPdfBytes(data);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500);

        // Check PDF Magic Bytes %PDF-
        String header = new String(pdfBytes, 0, 5);
        assertEquals("%PDF-", header);

        // Verify Apache PDFBox can parse it without error
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    @Test
    public void testGenerateVoidWatermarkedPdfReceipt() throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("receiptNumber", "RCP-2026-VOID01");
        data.put("paymentDate", "2026-03-29 15:00:00");
        data.put("paymentStatus", "REFUNDED");
        data.put("studentName", "Test Student");
        data.put("rollNumber", "715521104001");
        data.put("bookTitle", "Clean Code");
        data.put("amountPaid", new BigDecimal("15.00"));
        data.put("transactionRef", "UPI-REFUND-999");

        byte[] pdfBytes = pdfReceiptService.generateReceiptPdfBytes(data);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500);

        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }
}
