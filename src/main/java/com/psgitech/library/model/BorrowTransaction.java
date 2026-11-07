package com.psgitech.library.model;

import com.psgitech.library.enums.BorrowStatus;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Model representing a physical book borrowing transaction in the PSG iTech Library System.
 * Demonstrates OOP Encapsulation and Data Hiding.
 * Maps directly to table: borrow_transactions.
 */
public class BorrowTransaction {
    private Integer transactionId;
    private int studentId;
    private int bookId;
    private int copyId;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private LocalDate returnDate;
    private BorrowStatus status;
    private int issuedBy;
    private Integer returnedBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Supplementary joined metadata for presentation and reporting
    private String studentName;
    private String registerNumber;
    private String studentDepartment;
    private Integer studentYear;
    private String bookTitle;
    private String bookAuthor;
    private String copyCode;
    private String coverImageUrl;
    private long overdueDays;
    private long daysRemaining;
    private boolean overdue;

    // Feature 5 Automatic Allocation Result upon return
    private AllocationResult allocationResult;

    // Feature 6 Overdue Fine Record upon return
    private FineRecord fineRecord;

    // Feature 8 Interchange Display Metadata
    private boolean transferred;
    private String transferRecipientName;
    private LocalDate transferDate;
    private boolean acquiredViaInterchange;
    private String originalBorrowerName;

    // Feature Enhancement F3: Loan Renewals
    private int renewalCount = 0;
    private Timestamp lastRenewedAt;

    public BorrowTransaction() {
        this.status = BorrowStatus.ACTIVE;
    }

    public BorrowTransaction(int studentId, int bookId, int copyId, LocalDate issueDate,
                             LocalDate dueDate, int issuedBy) {
        this();
        this.studentId = studentId;
        this.bookId = bookId;
        this.copyId = copyId;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.issuedBy = issuedBy;
        this.status = BorrowStatus.ACTIVE;
    }

    public BorrowTransaction(Integer transactionId, int studentId, int bookId, int copyId,
                             LocalDate issueDate, LocalDate dueDate, LocalDate returnDate,
                             BorrowStatus status, int issuedBy, Integer returnedBy) {
        this.transactionId = transactionId;
        this.studentId = studentId;
        this.bookId = bookId;
        this.copyId = copyId;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
        this.status = status;
        this.issuedBy = issuedBy;
        this.returnedBy = returnedBy;
    }

    /**
     * Dynamically calculates overdue days and days remaining relative to an evaluation date.
     */
    public void computeDynamicMetrics(LocalDate evaluationDate) {
        if (evaluationDate == null) {
            evaluationDate = LocalDate.now();
        }

        if (this.status == BorrowStatus.ACTIVE) {
            if (this.dueDate != null) {
                if (evaluationDate.isAfter(this.dueDate)) {
                    this.overdue = true;
                    this.overdueDays = ChronoUnit.DAYS.between(this.dueDate, evaluationDate);
                    this.daysRemaining = -this.overdueDays;
                } else {
                    this.overdue = false;
                    this.overdueDays = 0;
                    this.daysRemaining = ChronoUnit.DAYS.between(evaluationDate, this.dueDate);
                }
            }
        } else {
            // Already returned
            this.overdue = false;
            if (this.returnDate != null && this.dueDate != null && this.returnDate.isAfter(this.dueDate)) {
                this.overdueDays = ChronoUnit.DAYS.between(this.dueDate, this.returnDate);
            } else {
                this.overdueDays = 0;
            }
            this.daysRemaining = 0;
        }
    }

    // Getters and Setters (Encapsulation)

    public Integer getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Integer transactionId) {
        this.transactionId = transactionId;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public int getBookId() {
        return bookId;
    }

    public void setBookId(int bookId) {
        this.bookId = bookId;
    }

    public int getCopyId() {
        return copyId;
    }

    public void setCopyId(int copyId) {
        this.copyId = copyId;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public BorrowStatus getStatus() {
        return status;
    }

    public void setStatus(BorrowStatus status) {
        this.status = status;
    }

    public int getIssuedBy() {
        return issuedBy;
    }

    public void setIssuedBy(int issuedBy) {
        this.issuedBy = issuedBy;
    }

    public Integer getReturnedBy() {
        return returnedBy;
    }

    public void setReturnedBy(Integer returnedBy) {
        this.returnedBy = returnedBy;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getRegisterNumber() {
        return registerNumber;
    }

    public void setRegisterNumber(String registerNumber) {
        this.registerNumber = registerNumber;
    }

    public String getStudentDepartment() {
        return studentDepartment;
    }

    public void setStudentDepartment(String studentDepartment) {
        this.studentDepartment = studentDepartment;
    }

    public Integer getStudentYear() {
        return studentYear;
    }

    public void setStudentYear(Integer studentYear) {
        this.studentYear = studentYear;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
        this.bookTitle = bookTitle;
    }

    public String getBookAuthor() {
        return bookAuthor;
    }

    public void setBookAuthor(String bookAuthor) {
        this.bookAuthor = bookAuthor;
    }

    public String getCopyCode() {
        return copyCode;
    }

    public void setCopyCode(String copyCode) {
        this.copyCode = copyCode;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public long getOverdueDays() {
        return overdueDays;
    }

    public void setOverdueDays(long overdueDays) {
        this.overdueDays = overdueDays;
    }

    public long getDaysRemaining() {
        return daysRemaining;
    }

    public void setDaysRemaining(long daysRemaining) {
        this.daysRemaining = daysRemaining;
    }

    public boolean isOverdue() {
        return overdue;
    }

    public void setOverdue(boolean overdue) {
        this.overdue = overdue;
    }

    public AllocationResult getAllocationResult() {
        return allocationResult;
    }

    public void setAllocationResult(AllocationResult allocationResult) {
        this.allocationResult = allocationResult;
    }

    public FineRecord getFineRecord() {
        return fineRecord;
    }

    public void setFineRecord(FineRecord fineRecord) {
        this.fineRecord = fineRecord;
    }

    public boolean isTransferred() {
        return transferred;
    }

    public void setTransferred(boolean transferred) {
        this.transferred = transferred;
    }

    public String getTransferRecipientName() {
        return transferRecipientName;
    }

    public void setTransferRecipientName(String transferRecipientName) {
        this.transferRecipientName = transferRecipientName;
    }

    public LocalDate getTransferDate() {
        return transferDate;
    }

    public void setTransferDate(LocalDate transferDate) {
        this.transferDate = transferDate;
    }

    public boolean isAcquiredViaInterchange() {
        return acquiredViaInterchange;
    }

    public void setAcquiredViaInterchange(boolean acquiredViaInterchange) {
        this.acquiredViaInterchange = acquiredViaInterchange;
    }

    public String getOriginalBorrowerName() {
        return originalBorrowerName;
    }

    public void setOriginalBorrowerName(String originalBorrowerName) {
        this.originalBorrowerName = originalBorrowerName;
    }

    public int getRenewalCount() {
        return renewalCount;
    }

    public void setRenewalCount(int renewalCount) {
        this.renewalCount = renewalCount;
    }

    public Timestamp getLastRenewedAt() {
        return lastRenewedAt;
    }

    public void setLastRenewedAt(Timestamp lastRenewedAt) {
        this.lastRenewedAt = lastRenewedAt;
    }

    @Override
    public String toString() {
        return "BorrowTransaction{" +
                "transactionId=" + transactionId +
                ", studentId=" + studentId +
                ", bookId=" + bookId +
                ", copyId=" + copyId +
                ", issueDate=" + issueDate +
                ", dueDate=" + dueDate +
                ", returnDate=" + returnDate +
                ", status=" + status +
                '}';
    }
}

