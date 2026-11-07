package com.psgitech.library.model;

import com.psgitech.library.enums.FineStatus;
import com.psgitech.library.exception.InvalidFineOperationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * Domain Model for an overdue textbook fine.
 * Strictly enforces OOP Encapsulation and Data Hiding.
 * Invariant: originalAmount = paidAmount + waivedAmount + outstandingAmount
 */
public class FineRecord {
    private Integer fineId;
    private int borrowTransactionId;
    private int studentId;
    private int bookId;
    private int copyId;
    private int overdueDays;
    private int chargeableDays;
    private BigDecimal finePerDay;
    private int gracePeriodDays;
    private BigDecimal maxFineApplied;
    private BigDecimal originalAmount;
    private BigDecimal paidAmount;
    private BigDecimal waivedAmount;
    private BigDecimal outstandingAmount;
    private FineStatus status;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Joined presentation metadata
    private String studentName;
    private String registerNumber;
    private String studentDepartment;
    private String bookTitle;
    private String bookIsbn;
    private String copyCode;
    private LocalDate dueDate;
    private LocalDate returnDate;

    public FineRecord() {
        this.paidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.waivedAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.status = FineStatus.UNPAID;
    }

    public FineRecord(int borrowTransactionId, int studentId, int bookId, int copyId,
                      FineCalculation calculation) {
        this();
        this.borrowTransactionId = borrowTransactionId;
        this.studentId = studentId;
        this.bookId = bookId;
        this.copyId = copyId;
        this.overdueDays = calculation.getOverdueDays();
        this.chargeableDays = calculation.getChargeableDays();
        this.finePerDay = calculation.getFinePerDay();
        this.gracePeriodDays = calculation.getGracePeriodDays();
        this.maxFineApplied = calculation.getMaxFineApplied();
        this.originalAmount = calculation.getFineAmount();
        this.outstandingAmount = calculation.getFineAmount();
        this.paidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.waivedAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.status = FineStatus.UNPAID;
    }

    /**
     * Domain Transition: Applies verified payment towards the outstanding balance.
     * Enforces the mathematical invariant O = P + W + R.
     */
    public void applyPayment(BigDecimal paymentAmount) {
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidFineOperationException("Payment amount must be greater than zero.");
        }
        if (paymentAmount.compareTo(this.outstandingAmount) > 0) {
            throw new InvalidFineOperationException("Payment amount (INR " + paymentAmount +
                    ") exceeds current outstanding balance (INR " + this.outstandingAmount + ").");
        }

        this.paidAmount = this.paidAmount.add(paymentAmount).setScale(2, RoundingMode.HALF_UP);
        this.outstandingAmount = this.outstandingAmount.subtract(paymentAmount).setScale(2, RoundingMode.HALF_UP);
        updateCalculatedStatus();
    }

    /**
     * Domain Transition: Applies approved administrative waiver.
     */
    public void applyWaiver(BigDecimal waiverAmount) {
        if (waiverAmount == null || waiverAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidFineOperationException("Waiver amount must be greater than zero.");
        }
        if (waiverAmount.compareTo(this.outstandingAmount) > 0) {
            throw new InvalidFineOperationException("Waiver amount (INR " + waiverAmount +
                    ") cannot exceed outstanding balance (INR " + this.outstandingAmount + ").");
        }

        this.waivedAmount = this.waivedAmount.add(waiverAmount).setScale(2, RoundingMode.HALF_UP);
        this.outstandingAmount = this.outstandingAmount.subtract(waiverAmount).setScale(2, RoundingMode.HALF_UP);
        updateCalculatedStatus();
    }

    /**
     * Domain Transition: Reverses a verified payment upon manual administrative refund.
     */
    public void reversePayment(BigDecimal refundAmount) {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidFineOperationException("Refund amount must be greater than zero.");
        }
        if (refundAmount.compareTo(this.paidAmount) > 0) {
            throw new InvalidFineOperationException("Refund amount (INR " + refundAmount +
                    ") exceeds total paid amount (INR " + this.paidAmount + ").");
        }

        this.paidAmount = this.paidAmount.subtract(refundAmount).setScale(2, RoundingMode.HALF_UP);
        this.outstandingAmount = this.outstandingAmount.add(refundAmount).setScale(2, RoundingMode.HALF_UP);
        updateCalculatedStatus();
    }

    private void updateCalculatedStatus() {
        if (this.outstandingAmount.compareTo(BigDecimal.ZERO) == 0) {
            if (this.paidAmount.compareTo(BigDecimal.ZERO) > 0) {
                this.status = FineStatus.PAID;
            } else if (this.waivedAmount.compareTo(BigDecimal.ZERO) > 0) {
                this.status = FineStatus.WAIVED;
            } else {
                this.status = FineStatus.PAID;
            }
        } else {
            if (this.paidAmount.compareTo(BigDecimal.ZERO) > 0 || this.waivedAmount.compareTo(BigDecimal.ZERO) > 0) {
                this.status = FineStatus.PARTIALLY_PAID;
            } else {
                this.status = FineStatus.UNPAID;
            }
        }
    }

    public Integer getFineId() {
        return fineId;
    }

    public void setFineId(Integer fineId) {
        this.fineId = fineId;
    }

    public int getBorrowTransactionId() {
        return borrowTransactionId;
    }

    public void setBorrowTransactionId(int borrowTransactionId) {
        this.borrowTransactionId = borrowTransactionId;
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

    public int getOverdueDays() {
        return overdueDays;
    }

    public void setOverdueDays(int overdueDays) {
        this.overdueDays = overdueDays;
    }

    public int getChargeableDays() {
        return chargeableDays;
    }

    public void setChargeableDays(int chargeableDays) {
        this.chargeableDays = chargeableDays;
    }

    public BigDecimal getFinePerDay() {
        return finePerDay;
    }

    public void setFinePerDay(BigDecimal finePerDay) {
        this.finePerDay = finePerDay;
    }

    public int getGracePeriodDays() {
        return gracePeriodDays;
    }

    public void setGracePeriodDays(int gracePeriodDays) {
        this.gracePeriodDays = gracePeriodDays;
    }

    public BigDecimal getMaxFineApplied() {
        return maxFineApplied;
    }

    public void setMaxFineApplied(BigDecimal maxFineApplied) {
        this.maxFineApplied = maxFineApplied;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public void setOriginalAmount(BigDecimal originalAmount) {
        this.originalAmount = originalAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public BigDecimal getWaivedAmount() {
        return waivedAmount;
    }

    public void setWaivedAmount(BigDecimal waivedAmount) {
        this.waivedAmount = waivedAmount;
    }

    public BigDecimal getOutstandingAmount() {
        return outstandingAmount;
    }

    public void setOutstandingAmount(BigDecimal outstandingAmount) {
        this.outstandingAmount = outstandingAmount;
    }

    public FineStatus getStatus() {
        return status;
    }

    public void setStatus(FineStatus status) {
        this.status = status;
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

    public String getBookTitle() {
        return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
        this.bookTitle = bookTitle;
    }

    public String getBookIsbn() {
        return bookIsbn;
    }

    public void setBookIsbn(String bookIsbn) {
        this.bookIsbn = bookIsbn;
    }

    public String getCopyCode() {
        return copyCode;
    }

    public void setCopyCode(String copyCode) {
        this.copyCode = copyCode;
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
}

