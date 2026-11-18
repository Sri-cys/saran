package com.psgitech.library.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.psgitech.library.enums.Role;
import com.psgitech.library.exception.*;
import com.psgitech.library.model.AllocationRecord;
import com.psgitech.library.model.BorrowTransaction;
import com.psgitech.library.model.Student;
import com.psgitech.library.model.User;
import com.psgitech.library.repository.AllocationRepository;
import com.psgitech.library.service.BorrowingService;
import com.psgitech.library.util.GsonProvider;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Servlet handling Book Issuance to students.
 * Restricted to Librarians and Administrators.
 * URL Mapping: /borrow/issue, /borrowings/issue
 */
@WebServlet(name = "IssueBookServlet", urlPatterns = {"/borrow/issue", "/borrowings/issue"})
public class IssueBookServlet extends HttpServlet {
    private BorrowingService borrowingService;
    private Gson gson;

    @Override
    public void init() {
        this.borrowingService = new BorrowingService();
        this.gson = GsonProvider.getGson();
    }

    private User getAuthenticatedUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return (session != null) ? (User) session.getAttribute("user") : null;
    }

    private boolean isStaff(User user) {
        return user != null && (user.getRole() == Role.LIBRARIAN || user.getRole() == Role.ADMIN);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        User currentUser = getAuthenticatedUser(req);
        if (currentUser == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Authentication required. Please log in.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        boolean isStaffUser = isStaff(currentUser);
        boolean isStudentUser = (currentUser.getRole() == Role.STUDENT);

        if (!isStaffUser && !isStudentUser) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Access denied. Only registered students and library staff can borrow or issue books.");
            resp.getWriter().write(gson.toJson(err));
            return;
        }

        String studentRegisterNumber = null;
        Integer copyId = null;
        Integer bookId = null;

        String contentType = req.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            try {
                JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                if (json.has("registerNumber")) studentRegisterNumber = json.get("registerNumber").getAsString();
                else if (json.has("studentRegisterNumber")) studentRegisterNumber = json.get("studentRegisterNumber").getAsString();
                if (json.has("copyId")) copyId = json.get("copyId").getAsInt();
                if (json.has("bookId")) bookId = json.get("bookId").getAsInt();
            } catch (Exception e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                Map<String, Object> err = new HashMap<>();
                err.put("success", false);
                err.put("message", "Invalid JSON request: " + e.getMessage());
                resp.getWriter().write(gson.toJson(err));
                return;
            }
        } else {
            studentRegisterNumber = req.getParameter("registerNumber");
            if (studentRegisterNumber == null) studentRegisterNumber = req.getParameter("studentRegisterNumber");
            String copyIdStr = req.getParameter("copyId");
            if (copyIdStr != null && !copyIdStr.trim().isEmpty()) {
                try {
                    copyId = Integer.parseInt(copyIdStr.trim());
                } catch (NumberFormatException ignored) {}
            }
            String bookIdStr = req.getParameter("bookId");
            if (bookIdStr != null && !bookIdStr.trim().isEmpty()) {
                try {
                    bookId = Integer.parseInt(bookIdStr.trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        Map<String, Object> responseData = new HashMap<>();

        // If student is borrowing directly
        if (isStudentUser) {
            studentRegisterNumber = currentUser.getIdentifier();
            if ((studentRegisterNumber == null || studentRegisterNumber.trim().isEmpty()) && currentUser instanceof Student) {
                studentRegisterNumber = ((Student) currentUser).getRegisterNumber();
            }
        }

        if (studentRegisterNumber == null || studentRegisterNumber.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseData.put("success", false);
            responseData.put("message", "Student register number is required.");
            resp.getWriter().write(gson.toJson(responseData));
            return;
        }

        if ((copyId == null || copyId <= 0) && (bookId == null || bookId <= 0)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseData.put("success", false);
            responseData.put("message", "Valid book ID or physical copy ID is required.");
            resp.getWriter().write(gson.toJson(responseData));
            return;
        }

        try {
            BorrowTransaction tx;
            boolean collectedFromAllocation = false;

            if (isStudentUser && (copyId == null || copyId <= 0) && bookId != null && bookId > 0) {
                // Direct student self-borrow by book ID
                tx = borrowingService.borrowBookForStudent(currentUser.getUserId(), bookId);
            } else {
                // Resolved or explicit copy ID
                if ((copyId == null || copyId <= 0) && bookId != null && bookId > 0) {
                    com.psgitech.library.repository.BookRepository bookRepo = new com.psgitech.library.repository.BookRepository();
                    copyId = bookRepo.findFirstAvailableCopyId(bookId);
                    if (copyId <= 0) {
                        throw new BookCopyUnavailableException("All physical copies of this book are currently borrowed or on hold.");
                    }
                }

                // Check if copy was held under an active allocation
                AllocationRepository allocRepo = new AllocationRepository();
                AllocationRecord priorAlloc = allocRepo.findActiveByCopy(copyId);
                collectedFromAllocation = (priorAlloc != null);

                tx = borrowingService.issueBook(studentRegisterNumber, copyId, currentUser.getUserId());
            }

            resp.setStatus(HttpServletResponse.SC_CREATED);
            responseData.put("success", true);
            responseData.put("message", collectedFromAllocation ?
                    "Book collected and issued successfully from hold allocation." :
                    "Book borrowed successfully! Enjoy your reading.");

            Map<String, Object> txData = new HashMap<>();
            txData.put("transactionId", tx.getTransactionId());
            txData.put("bookTitle", tx.getBookTitle());
            txData.put("copyCode", tx.getCopyCode());
            txData.put("issueDate", tx.getIssueDate() != null ? tx.getIssueDate().toString() : null);
            txData.put("dueDate", tx.getDueDate() != null ? tx.getDueDate().toString() : null);
            txData.put("status", tx.getStatus().name());
            txData.put("collectedFromAllocation", collectedFromAllocation);

            responseData.put("data", txData);
            resp.getWriter().write(gson.toJson(responseData));

        } catch (StudentNotFoundException | BookNotFoundException e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            responseData.put("success", false);
            responseData.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(responseData));

        } catch (InvalidAllocationException | AllocationFailedException e) {
            // Section 9.2: 409 Conflict when held for another student or state conflict
            resp.setStatus(HttpServletResponse.SC_CONFLICT);
            responseData.put("success", false);
            responseData.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(responseData));

        } catch (BookCopyUnavailableException | BorrowingLimitExceededException |
                 StudentNotEligibleException | InvalidBorrowingOperationException | IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            responseData.put("success", false);
            responseData.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(responseData));

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            responseData.put("success", false);
            responseData.put("message", "An unexpected error occurred during book issuance.");
            resp.getWriter().write(gson.toJson(responseData));
        }
    }
}

