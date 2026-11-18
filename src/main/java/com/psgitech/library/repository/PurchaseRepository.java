package com.psgitech.library.repository;

import com.psgitech.library.model.BookPurchase;
import com.psgitech.library.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PurchaseRepository {
    private static final Logger LOGGER = Logger.getLogger(PurchaseRepository.class.getName());

    public List<BookPurchase> findPurchases(String supplier, LocalDate fromDate, LocalDate toDate, int offset, int limit) {
        StringBuilder sql = new StringBuilder(
            "SELECT bp.*, u.name AS creator_name " +
            "FROM book_purchases bp " +
            "LEFT JOIN users u ON bp.created_by = u.user_id " +
            "WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();
        if (supplier != null && !supplier.trim().isEmpty()) {
            sql.append("AND bp.supplier_name LIKE ? ");
            params.add("%" + supplier.trim() + "%");
        }
        if (fromDate != null) {
            sql.append("AND bp.purchase_date >= ? ");
            params.add(Date.valueOf(fromDate));
        }
        if (toDate != null) {
            sql.append("AND bp.purchase_date <= ? ");
            params.add(Date.valueOf(toDate));
        }

        sql.append("ORDER BY bp.purchase_date DESC, bp.purchase_id DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<BookPurchase> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to find purchases", e);
        }
        return list;
    }

    public Map<String, Object> getSummary(LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) AS total_invoices, " +
            "COALESCE(SUM(quantity), 0) AS total_items, " +
            "COALESCE(SUM(total_amount), 0.00) AS total_expenditure, " +
            "COUNT(DISTINCT supplier_name) AS unique_suppliers " +
            "FROM book_purchases WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();
        if (fromDate != null) {
            sql.append("AND purchase_date >= ? ");
            params.add(Date.valueOf(fromDate));
        }
        if (toDate != null) {
            sql.append("AND purchase_date <= ? ");
            params.add(Date.valueOf(toDate));
        }

        Map<String, Object> map = new HashMap<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    map.put("totalInvoices", rs.getInt("total_invoices"));
                    map.put("totalItems", rs.getInt("total_items"));
                    map.put("totalExpenditure", rs.getBigDecimal("total_expenditure"));
                    map.put("uniqueSuppliers", rs.getInt("unique_suppliers"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to get purchase summary", e);
        }
        return map;
    }

    public int createPurchase(BookPurchase p) throws SQLException {
        String sql = "INSERT INTO book_purchases (supplier_name, invoice_no, purchase_date, unit_price, " +
                     "quantity, total_amount, request_id, created_by, remarks, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getSupplierName());
            ps.setString(2, p.getInvoiceNo());
            ps.setDate(3, p.getPurchaseDate());
            ps.setBigDecimal(4, p.getUnitPrice());
            ps.setInt(5, p.getQuantity());
            ps.setBigDecimal(6, p.getTotalAmount());
            if (p.getRequestId() != null) {
                ps.setInt(7, p.getRequestId());
            } else {
                ps.setNull(7, Types.INTEGER);
            }
            ps.setInt(8, p.getCreatedBy());
            ps.setString(9, p.getRemarks());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public boolean existsInvoice(String supplierName, String invoiceNo) {
        String sql = "SELECT COUNT(*) FROM book_purchases WHERE supplier_name = ? AND invoice_no = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, supplierName);
            ps.setString(2, invoiceNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to check invoice uniqueness", e);
        }
        return false;
    }

    private BookPurchase mapRow(ResultSet rs) throws SQLException {
        BookPurchase p = new BookPurchase();
        p.setPurchaseId(rs.getInt("purchase_id"));
        p.setSupplierName(rs.getString("supplier_name"));
        p.setInvoiceNo(rs.getString("invoice_no"));
        p.setPurchaseDate(rs.getDate("purchase_date"));
        p.setUnitPrice(rs.getBigDecimal("unit_price"));
        p.setQuantity(rs.getInt("quantity"));
        p.setTotalAmount(rs.getBigDecimal("total_amount"));
        int reqId = rs.getInt("request_id");
        if (!rs.wasNull()) {
            p.setRequestId(reqId);
        }
        p.setCreatedBy(rs.getInt("created_by"));
        p.setCreatorName(rs.getString("creator_name"));
        p.setRemarks(rs.getString("remarks"));
        p.setCreatedAt(rs.getTimestamp("created_at"));
        p.setUpdatedAt(rs.getTimestamp("updated_at"));
        return p;
    }
}
