package com.psgitech.library.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.util.Matrix;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * Service generating institutional PDF receipts using Apache PDFBox.
 * Conforms to Times New Roman institutional styling, navy branding (#203B60),
 * detailed fine breakdown, and security watermarks.
 */
public class PdfReceiptService {

    private static final Color NAVY = new Color(32, 59, 96);
    private static final Color LIGHT_BG = new Color(248, 246, 240);
    private static final Color BORDER_COLOR = new Color(226, 232, 240);
    private static final Color TEXT_DARK = new Color(30, 41, 59);
    private static final Color TEXT_MUTED = new Color(100, 116, 139);
    private static final Color SUCCESS_GREEN = new Color(21, 155, 80);
    private static final Color WATERMARK_RED = new Color(220, 38, 38, 70);

    public byte[] generateReceiptPdfBytes(Map<String, Object> receiptData) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeReceiptPdf(receiptData, baos);
        return baos.toByteArray();
    }

    public void writeReceiptPdf(Map<String, Object> receiptData, OutputStream out) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                // 1. Header Banner Background (Navy #203B60)
                float bannerHeight = 90;
                float bannerY = pageHeight - bannerHeight;
                cs.setNonStrokingColor(NAVY);
                cs.addRect(0, bannerY, pageWidth, bannerHeight);
                cs.fill();

                // Header Text
                cs.setNonStrokingColor(Color.WHITE);
                PDFont fontBold = PDType1Font.TIMES_BOLD;
                PDFont fontRegular = PDType1Font.TIMES_ROMAN;

                drawCenteredText(cs, fontBold, 15, "PSG INSTITUTE OF TECHNOLOGY AND APPLIED RESEARCH", pageWidth, bannerY + 60);
                drawCenteredText(cs, fontRegular, 10, "Neelambur, Coimbatore - 641 062 | Central Library & Information Center", pageWidth, bannerY + 44);
                drawCenteredText(cs, fontBold, 12, "OFFICIAL FINE PAYMENT RECEIPT", pageWidth, bannerY + 24);

                // Thin gold/warm accent bar below navy header
                cs.setNonStrokingColor(new Color(197, 139, 36));
                cs.addRect(0, bannerY - 4, pageWidth, 4);
                cs.fill();

                float currentY = bannerY - 35;

                // 2. Receipt Reference Metadata
                String receiptNo = String.valueOf(receiptData.getOrDefault("receiptNumber", "N/A"));
                String paymentDate = String.valueOf(receiptData.getOrDefault("paymentDate", ""));
                if (paymentDate.length() > 19) paymentDate = paymentDate.substring(0, 19);
                String status = String.valueOf(receiptData.getOrDefault("status", "SUCCESS"));

                cs.setFont(fontBold, 10);
                cs.setNonStrokingColor(NAVY);
                cs.beginText();
                cs.newLineAtOffset(50, currentY);
                cs.showText("Receipt No: " + receiptNo);
                cs.endText();

                cs.setFont(fontRegular, 10);
                cs.setNonStrokingColor(TEXT_DARK);
                cs.beginText();
                cs.newLineAtOffset(pageWidth - 220, currentY);
                cs.showText("Payment Date: " + paymentDate);
                cs.endText();

                currentY -= 20;

                // Status Badge
                cs.setFont(fontBold, 9);
                if ("SUCCESS".equalsIgnoreCase(status) || "VERIFIED".equalsIgnoreCase(status)) {
                    cs.setNonStrokingColor(SUCCESS_GREEN);
                } else if ("REFUNDED".equalsIgnoreCase(status)) {
                    cs.setNonStrokingColor(new Color(192, 57, 43));
                } else {
                    cs.setNonStrokingColor(NAVY);
                }
                cs.beginText();
                cs.newLineAtOffset(50, currentY);
                cs.showText("Status: " + status.toUpperCase());
                cs.endText();

                currentY -= 20;

                // 3. Student & Academic Information Box
                float boxWidth = pageWidth - 100;
                float boxHeight = 65;
                cs.setNonStrokingColor(LIGHT_BG);
                cs.addRect(50, currentY - boxHeight, boxWidth, boxHeight);
                cs.fill();

                cs.setStrokingColor(BORDER_COLOR);
                cs.setLineWidth(1);
                cs.addRect(50, currentY - boxHeight, boxWidth, boxHeight);
                cs.stroke();

                String studentName = String.valueOf(receiptData.getOrDefault("studentName", "Student"));
                String regNo = String.valueOf(receiptData.getOrDefault("registerNumber", "-"));
                String bookTitle = String.valueOf(receiptData.getOrDefault("bookTitle", "Library Resource"));

                float infoTextY = currentY - 20;
                cs.setNonStrokingColor(TEXT_DARK);

                cs.setFont(fontBold, 10);
                drawText(cs, 65, infoTextY, "Student Name:");
                cs.setFont(fontRegular, 10);
                drawText(cs, 150, infoTextY, studentName);

                cs.setFont(fontBold, 10);
                drawText(cs, 340, infoTextY, "Register No:");
                cs.setFont(fontRegular, 10);
                drawText(cs, 415, infoTextY, regNo);

                infoTextY -= 20;
                cs.setFont(fontBold, 10);
                drawText(cs, 65, infoTextY, "Catalog Book:");
                cs.setFont(fontRegular, 10);
                if (bookTitle.length() > 50) bookTitle = bookTitle.substring(0, 47) + "...";
                drawText(cs, 150, infoTextY, bookTitle);

                cs.setFont(fontBold, 10);
                drawText(cs, 340, infoTextY, "Fine Ref ID:");
                cs.setFont(fontRegular, 10);
                drawText(cs, 415, infoTextY, "#" + receiptData.getOrDefault("fineId", "-"));

                currentY -= (boxHeight + 25);

                // 4. Financial Breakdown Table
                cs.setNonStrokingColor(NAVY);
                cs.setFont(fontBold, 11);
                drawText(cs, 50, currentY, "PAYMENT BREAKDOWN");

                currentY -= 12;

                // Table Header Row
                float rowHeight = 22;
                cs.setNonStrokingColor(NAVY);
                cs.addRect(50, currentY - rowHeight, boxWidth, rowHeight);
                cs.fill();

                cs.setNonStrokingColor(Color.WHITE);
                cs.setFont(fontBold, 9);
                drawText(cs, 65, currentY - 15, "DESCRIPTION / PARTICULARS");
                drawText(cs, 430, currentY - 15, "AMOUNT (INR)");

                currentY -= rowHeight;

                // Table Rows
                String[][] tableData = {
                    {"Original Assessed Overdue Fine", "INR " + receiptData.getOrDefault("originalFineAmount", "0.00")},
                    {"Total Fine Amount Waived (Admin)", "INR " + receiptData.getOrDefault("totalWaived", "0.00")},
                    {"Total Paid Prior to this Payment", "INR " + calculatePriorPaid(receiptData)},
                    {"Amount Paid (This Transaction)", "INR " + receiptData.getOrDefault("amountPaidThisPayment", "0.00")},
                    {"Remaining Outstanding Balance", "INR " + receiptData.getOrDefault("remainingOutstanding", "0.00")},
                    {"Payment Method", String.valueOf(receiptData.getOrDefault("paymentMethod", "UPI"))},
                    {"Bank UPI Reference (UTR)", maskUtr(String.valueOf(receiptData.getOrDefault("referenceId", "")))}
                };

                for (int i = 0; i < tableData.length; i++) {
                    String label = tableData[i][0];
                    String val = tableData[i][1];

                    if (i % 2 == 1) {
                        cs.setNonStrokingColor(LIGHT_BG);
                        cs.addRect(50, currentY - rowHeight, boxWidth, rowHeight);
                        cs.fill();
                    }

                    cs.setStrokingColor(BORDER_COLOR);
                    cs.setLineWidth(0.5f);
                    cs.addRect(50, currentY - rowHeight, boxWidth, rowHeight);
                    cs.stroke();

                    cs.setNonStrokingColor(TEXT_DARK);
                    boolean isHighlight = (i == 3); // Amount Paid
                    cs.setFont(isHighlight ? fontBold : fontRegular, 9);

                    drawText(cs, 65, currentY - 15, label);
                    drawText(cs, 430, currentY - 15, val);

                    currentY -= rowHeight;
                }

                currentY -= 35;

                // 5. Certification and Security Verification Notice
                cs.setNonStrokingColor(TEXT_MUTED);
                cs.setFont(fontRegular, 8);
                drawText(cs, 50, currentY, "Verification Hash / Idempotency Ref: " + receiptData.getOrDefault("paymentCode", "-"));
                currentY -= 14;
                drawText(cs, 50, currentY, "Computer-generated electronic receipt issued by PSG iTech Central Library Management System.");
                currentY -= 12;
                drawText(cs, 50, currentY, "Valid without physical seal or manual signature under Library Resource Regulations.");

                // Sign-off signature block on right
                cs.setFont(fontBold, 9);
                cs.setNonStrokingColor(NAVY);
                drawText(cs, pageWidth - 230, currentY + 12, "PSG iTech Central Library");
                cs.setFont(fontRegular, 8);
                cs.setNonStrokingColor(TEXT_DARK);
                drawText(cs, pageWidth - 230, currentY, "Circulation & Fine Reconciliation Desk");

                // 6. Watermark if refunded
                boolean isRefunded = Boolean.TRUE.equals(receiptData.get("isRefunded"));
                if (isRefunded) {
                    cs.saveGraphicsState();
                    cs.setNonStrokingColor(WATERMARK_RED);
                    cs.setFont(fontBold, 48);
                    cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(35), 180, 350));
                    cs.beginText();
                    cs.showText("VOID / REFUNDED");
                    cs.endText();
                    cs.restoreGraphicsState();
                }
            }

            document.save(out);
        }
    }

    private static void drawText(PDPageContentStream cs, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(text != null ? text : "");
        cs.endText();
    }

    private static void drawCenteredText(PDPageContentStream cs, PDFont font, float fontSize,
                                         String text, float pageWidth, float y) throws IOException {
        float stringWidth = font.getStringWidth(text) / 1000 * fontSize;
        float x = (pageWidth - stringWidth) / 2;
        cs.setFont(font, fontSize);
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private static String maskUtr(String utr) {
        if (utr == null || utr.trim().isEmpty()) return "N/A";
        utr = utr.trim();
        if (utr.length() <= 4) return utr;
        int unmasked = 4;
        int masked = utr.length() - unmasked;
        return "X".repeat(masked) + utr.substring(masked);
    }

    private static String calculatePriorPaid(Map<String, Object> data) {
        try {
            java.math.BigDecimal totalPaid = new java.math.BigDecimal(String.valueOf(data.getOrDefault("totalPaidSoFar", "0.00")));
            java.math.BigDecimal thisPaid = new java.math.BigDecimal(String.valueOf(data.getOrDefault("amountPaidThisPayment", "0.00")));
            java.math.BigDecimal prior = totalPaid.subtract(thisPaid);
            if (prior.compareTo(java.math.BigDecimal.ZERO) < 0) prior = java.math.BigDecimal.ZERO;
            return prior.setScale(2).toPlainString();
        } catch (Exception e) {
            return "0.00";
        }
    }
}

