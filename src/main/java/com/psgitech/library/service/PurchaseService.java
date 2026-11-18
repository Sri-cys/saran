package com.psgitech.library.service;

import com.psgitech.library.exception.ValidationException;
import com.psgitech.library.model.BookPurchase;
import com.psgitech.library.repository.PurchaseRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class PurchaseService {
    private final PurchaseRepository repository;
    private final ActivityLogService activityLogService;

    public PurchaseService() {
        this.repository = new PurchaseRepository();
        this.activityLogService = new ActivityLogService();
    }

    public PurchaseService(PurchaseRepository repository, ActivityLogService activityLogService) {
        this.repository = repository;
        this.activityLogService = activityLogService;
    }

    public List<BookPurchase> getPurchases(String supplier, LocalDate fromDate, LocalDate toDate, int page, int pageSize) {
        int clampedPage = Math.max(1, page);
        int clampedSize = Math.max(1, Math.min(100, pageSize));
        int offset = (clampedPage - 1) * clampedSize;
        return repository.findPurchases(supplier, fromDate, toDate, offset, clampedSize);
    }

    public Map<String, Object> getSummary(LocalDate fromDate, LocalDate toDate) {
        return repository.getSummary(fromDate, toDate);
    }

    public int recordPurchase(BookPurchase p, int staffId) {
        if (p.getSupplierName() == null || p.getSupplierName().trim().isEmpty()) {
            throw new ValidationException("Supplier name is required.");
        }
        if (p.getInvoiceNo() == null || p.getInvoiceNo().trim().isEmpty()) {
            throw new ValidationException("Invoice number is required.");
        }
        if (p.getPurchaseDate() == null) {
            throw new ValidationException("Purchase date is required.");
        }
        if (p.getUnitPrice() == null || p.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Unit price must be strictly greater than zero.");
        }
        if (p.getQuantity() <= 0) {
            throw new ValidationException("Quantity must be at least 1.");
        }

        // Server computes total amount
        BigDecimal total = p.getUnitPrice().multiply(BigDecimal.valueOf(p.getQuantity()));
        p.setTotalAmount(total);
        p.setCreatedBy(staffId);

        // Check unique invoice constraint per supplier
        if (repository.existsInvoice(p.getSupplierName().trim(), p.getInvoiceNo().trim())) {
            throw new ValidationException("Invoice #" + p.getInvoiceNo() + " already exists for supplier '" + p.getSupplierName() + "'.");
        }

        try {
            int id = repository.createPurchase(p);
            if (id > 0) {
                activityLogService.logEvent("BOOK_PURCHASE_RECORDED", staffId, "LIBRARIAN",
                        "BOOK_PURCHASE", String.valueOf(id),
                        "Recorded invoice #" + p.getInvoiceNo() + " from " + p.getSupplierName() + " for total Rs." + total, null);
            }
            return id;
        } catch (SQLException e) {
            throw new RuntimeException("Database error recording purchase: " + e.getMessage(), e);
        }
    }
}
