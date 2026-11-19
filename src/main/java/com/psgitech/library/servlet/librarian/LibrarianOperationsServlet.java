package com.psgitech.library.servlet.librarian;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.dto.librarian.ApiResponse;
import com.psgitech.library.exception.ValidationException;
import com.psgitech.library.model.AcquisitionRequest;
import com.psgitech.library.model.Book;
import com.psgitech.library.model.BookPurchase;
import com.psgitech.library.model.LibraryQuery;
import com.psgitech.library.model.User;
import com.psgitech.library.repository.BorrowRepository;
import com.psgitech.library.service.AcquisitionService;
import com.psgitech.library.service.CsvImportService;
import com.psgitech.library.service.PurchaseService;
import com.psgitech.library.service.QueryService;
import com.psgitech.library.service.FloorLayoutService;
import com.psgitech.library.model.LibraryLayout;
import com.psgitech.library.model.HighDemandAlert;
import com.psgitech.library.util.DBConnection;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "LibrarianOperationsServlet", urlPatterns = {"/api/librarian/operations/*", "/api/librarian/layout/*"})
public class LibrarianOperationsServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(LibrarianOperationsServlet.class.getName());

    private AcquisitionService acquisitionService;
    private CsvImportService csvImportService;
    private QueryService queryService;
    private PurchaseService purchaseService;
    private BorrowRepository borrowRepository;
    private FloorLayoutService floorLayoutService;
    private Gson gson;

    @Override
    public void init() {
        this.acquisitionService = new AcquisitionService();
        this.csvImportService = new CsvImportService();
        this.queryService = new QueryService();
        this.purchaseService = new PurchaseService();
        this.borrowRepository = new BorrowRepository();
        this.floorLayoutService = new FloorLayoutService();
        this.gson = GsonProvider.getGson();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());

        try {
            if (path.endsWith("/import-csv/template")) {
                handleDownloadTemplate(resp);
                return;
            }

            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");

            if (path.contains("/acquisitions")) {
                handleGetAcquisitions(req, resp);
            } else if (path.contains("/queries")) {
                handleGetQueries(req, resp);
            } else if (path.endsWith("/purchases/summary")) {
                handleGetPurchaseSummary(req, resp);
            } else if (path.contains("/purchases")) {
                handleGetPurchases(req, resp);
            } else if (path.endsWith("/copy-lookup")) {
                handleCopyLookup(req, resp);
            } else if (path.endsWith("/layout/floors") || path.endsWith("/floors")) {
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(ApiResponse.ok(floorLayoutService.getFloors())));
            } else if (path.endsWith("/layout/grid") || path.endsWith("/layout/heatmap") || path.endsWith("/grid") || path.endsWith("/heatmap")) {
                String floor = req.getParameter("floor");
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(ApiResponse.ok(floorLayoutService.getFloorHeatmap(floor))));
            } else if (path.endsWith("/high-demand-alerts")) {
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(gson.toJson(ApiResponse.ok(floorLayoutService.getHighDemandAlerts())));
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "Endpoint not found: " + path)));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Operations GET error: " + path, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(ApiResponse.error("INTERNAL_ERROR", e.getMessage())));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String path = req.getRequestURI().substring(req.getContextPath().length());

        try {
            if (path.endsWith("/acquisitions/status")) {
                handleUpdateAcquisitionStatus(req, resp);
            } else if (path.endsWith("/acquisitions/convert-to-inventory")) {
                handleConvertAcquisition(req, resp);
            } else if (path.endsWith("/import-csv/preview")) {
                handleCsvPreview(req, resp);
            } else if (path.endsWith("/import-csv/commit")) {
                handleCsvCommit(req, resp);
            } else if (path.endsWith("/queries/reply")) {
                handleQueryReply(req, resp);
            } else if (path.endsWith("/purchases")) {
                handleCreatePurchase(req, resp);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "Endpoint not found: " + path)));
            }
        } catch (ValidationException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(ApiResponse.error("VALIDATION_ERROR", e.getMessage())));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Operations POST error: " + path, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(gson.toJson(ApiResponse.error("INTERNAL_ERROR", e.getMessage())));
        }
    }

    private void handleDownloadTemplate(HttpServletResponse resp) throws IOException {
        resp.setContentType("text/csv; charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"book_import_template.csv\"");
        String template = csvImportService.generateTemplate();
        resp.getOutputStream().write(template.getBytes(StandardCharsets.UTF_8));
    }

    private void handleGetAcquisitions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String status = req.getParameter("status");
        String department = req.getParameter("department");
        int page = parseInt(req.getParameter("page"), 1);
        int limit = parseInt(req.getParameter("limit"), 20);

        List<AcquisitionRequest> list = acquisitionService.getRequests(status, department, page, limit);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(list)));
    }

    private void handleUpdateAcquisitionStatus(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        int requestId = json.get("requestId").getAsInt();
        String status = json.get("status").getAsString();
        String remarks = json.has("remarks") ? json.get("remarks").getAsString() : "";

        User staff = getAuthenticatedStaff(req);
        boolean ok = acquisitionService.updateStatus(requestId, status, remarks, staff.getUserId());
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(Map.of("success", ok, "requestId", requestId, "status", status))));
    }

    private void handleConvertAcquisition(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        int requestId = json.get("requestId").getAsInt();
        String category = json.has("category") ? json.get("category").getAsString() : null;
        int copies = json.has("copies") ? json.get("copies").getAsInt() : 1;
        String floor = json.has("floor") ? json.get("floor").getAsString() : "1";
        String block = json.has("block") ? json.get("block").getAsString() : "Main";
        String section = json.has("section") ? json.get("section").getAsString() : "A";
        String rack = json.has("rack") ? json.get("rack").getAsString() : "R1";
        String shelf = json.has("shelf") ? json.get("shelf").getAsString() : "S1";

        User staff = getAuthenticatedStaff(req);
        Book book = acquisitionService.convertToInventory(requestId, category, copies, floor, block, section, rack, shelf, staff.getUserId());

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(book)));
    }

    private void handleCsvPreview(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        String csvContent = json.get("csvContent").getAsString();

        List<CsvImportService.CsvRowPreview> preview = csvImportService.previewCsv(csvContent);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(preview)));
    }

    private void handleCsvCommit(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        boolean skipDuplicates = json.has("skipDuplicates") && json.get("skipDuplicates").getAsBoolean();

        List<CsvImportService.CsvRowPreview> rows = gson.fromJson(
                json.get("rows"),
                new com.google.gson.reflect.TypeToken<List<CsvImportService.CsvRowPreview>>(){}.getType()
        );

        User staff = getAuthenticatedStaff(req);
        Map<String, Object> result = csvImportService.commitBatch(rows, skipDuplicates, staff.getUserId());

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(result)));
    }

    private void handleGetQueries(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String status = req.getParameter("status");
        String category = req.getParameter("category");
        int page = parseInt(req.getParameter("page"), 1);
        int limit = parseInt(req.getParameter("limit"), 20);

        List<LibraryQuery> list = queryService.getQueries(status, category, page, limit);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(list)));
    }

    private void handleQueryReply(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        int queryId = json.get("queryId").getAsInt();
        String responseText = json.get("responseText").getAsString();

        User staff = getAuthenticatedStaff(req);
        boolean ok = queryService.replyQuery(queryId, responseText, staff.getUserId());

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(Map.of("success", ok, "queryId", queryId))));
    }

    private void handleGetPurchases(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String supplier = req.getParameter("supplier");
        String fromStr = req.getParameter("from");
        String toStr = req.getParameter("to");
        LocalDate from = (fromStr != null && !fromStr.isEmpty()) ? LocalDate.parse(fromStr) : null;
        LocalDate to = (toStr != null && !toStr.isEmpty()) ? LocalDate.parse(toStr) : null;
        int page = parseInt(req.getParameter("page"), 1);
        int limit = parseInt(req.getParameter("limit"), 20);

        List<BookPurchase> list = purchaseService.getPurchases(supplier, from, to, page, limit);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(list)));
    }

    private void handleGetPurchaseSummary(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String fromStr = req.getParameter("from");
        String toStr = req.getParameter("to");
        LocalDate from = (fromStr != null && !fromStr.isEmpty()) ? LocalDate.parse(fromStr) : null;
        LocalDate to = (toStr != null && !toStr.isEmpty()) ? LocalDate.parse(toStr) : null;

        Map<String, Object> summary = purchaseService.getSummary(from, to);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(summary)));
    }

    private void handleCreatePurchase(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonObject json = parseJsonBody(req);
        BookPurchase p = new BookPurchase();
        p.setSupplierName(json.get("supplierName").getAsString());
        p.setInvoiceNo(json.get("invoiceNo").getAsString());
        p.setPurchaseDate(Date.valueOf(json.get("purchaseDate").getAsString()));
        p.setUnitPrice(new BigDecimal(json.get("unitPrice").getAsString()));
        p.setQuantity(json.get("quantity").getAsInt());
        if (json.has("requestId") && !json.get("requestId").isJsonNull()) {
            p.setRequestId(json.get("requestId").getAsInt());
        }
        if (json.has("remarks")) {
            p.setRemarks(json.get("remarks").getAsString());
        }

        User staff = getAuthenticatedStaff(req);
        int id = purchaseService.recordPurchase(p, staff.getUserId());

        p.setPurchaseId(id);
        resp.setStatus(HttpServletResponse.SC_CREATED);
        resp.getWriter().write(gson.toJson(ApiResponse.ok(p)));
    }

    private void handleCopyLookup(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String code = req.getParameter("code");
        if (code == null || code.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write(gson.toJson(ApiResponse.error("BAD_REQUEST", "Copy code or barcode is required.")));
            return;
        }

        String sql = "SELECT bc.copy_id, bc.book_id, bc.copy_code, bc.barcode, bc.status, " +
                     "b.title, b.author, b.isbn, b.category, " +
                     "bl.floor, bl.block, bl.section, bl.rack, bl.shelf, " +
                     "bt.transaction_id, bt.student_id, u.name AS borrower_name, u.email AS borrower_reg_no, bt.due_date " +
                     "FROM book_copies bc " +
                     "JOIN books b ON bc.book_id = b.book_id " +
                     "LEFT JOIN book_locations bl ON (bl.copy_id = bc.copy_id OR (bl.book_id = bc.book_id AND bl.copy_id IS NULL)) " +
                     "LEFT JOIN borrow_transactions bt ON (bt.copy_id = bc.copy_id AND bt.status = 'ACTIVE') " +
                     "LEFT JOIN users u ON bt.student_id = u.user_id " +
                     "WHERE bc.copy_code = ? OR bc.barcode = ? OR CAST(bc.copy_id AS CHAR) = ? " +
                     "LIMIT 1";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code.trim());
            ps.setString(2, code.trim());
            ps.setString(3, code.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("copyId", rs.getInt("copy_id"));
                    map.put("bookId", rs.getInt("book_id"));
                    map.put("copyCode", rs.getString("copy_code"));
                    map.put("barcode", rs.getString("barcode"));
                    map.put("status", rs.getString("status"));
                    map.put("title", rs.getString("title"));
                    map.put("author", rs.getString("author"));
                    map.put("isbn", rs.getString("isbn"));
                    map.put("category", rs.getString("category"));
                    map.put("floor", rs.getString("floor"));
                    map.put("block", rs.getString("block"));
                    map.put("section", rs.getString("section"));
                    map.put("rack", rs.getString("rack"));
                    map.put("shelf", rs.getString("shelf"));

                    int txId = rs.getInt("transaction_id");
                    if (!rs.wasNull()) {
                        map.put("activeTransactionId", txId);
                        map.put("borrowerStudentId", rs.getInt("student_id"));
                        map.put("borrowerName", rs.getString("borrower_name"));
                        map.put("borrowerRegNo", rs.getString("borrower_reg_no"));
                        map.put("dueDate", rs.getDate("due_date") != null ? rs.getDate("due_date").toString() : "");
                    }

                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.getWriter().write(gson.toJson(ApiResponse.ok(map)));
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to lookup copy: " + code, e);
        }

        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        resp.getWriter().write(gson.toJson(ApiResponse.error("NOT_FOUND", "No physical copy found for code: " + code)));
    }

    private User getAuthenticatedStaff(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            return (User) session.getAttribute("user");
        }
        throw new ValidationException("Authentication session expired.");
    }

    private JsonObject parseJsonBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        if (sb.length() == 0) return new JsonObject();
        return JsonParser.parseString(sb.toString()).getAsJsonObject();
    }

    private int parseInt(String str, int def) {
        if (str == null || str.trim().isEmpty()) return def;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
