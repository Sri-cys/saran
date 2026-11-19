package com.psgitech.library.model;

import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * Model representing a one-time-per-student goodwill due date extension granted by library staff.
 * Table: goodwill_extensions.
 */
public class GoodwillExtension {
    private Integer id;
    private int studentId;
    private int transactionId;
    private int extraDays;
    private String reason;
    private LocalDate previousDueDate;
    private LocalDate newDueDate;
    private int grantedBy;
    private Timestamp grantedAt;

    // Joined metadata for UI display
    private String studentName;
    private String registerNumber;
    private String bookTitle;
    private String grantedByName;

    public GoodwillExtension() {}

    public GoodwillExtension(int studentId, int transactionId, int extraDays, String reason,
                             LocalDate previousDueDate, LocalDate newDueDate, int grantedBy) {
        this.studentId = studentId;
        this.transactionId = transactionId;
        this.extraDays = extraDays;
        this.reason = reason;
        this.previousDueDate = previousDueDate;
        this.newDueDate = newDueDate;
        this.grantedBy = grantedBy;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(int transactionId) {
        this.transactionId = transactionId;
    }

    public int getExtraDays() {
        return extraDays;
    }

    public void setExtraDays(int extraDays) {
        this.extraDays = extraDays;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDate getPreviousDueDate() {
        return previousDueDate;
    }

    public void setPreviousDueDate(LocalDate previousDueDate) {
        this.previousDueDate = previousDueDate;
    }

    public LocalDate getNewDueDate() {
        return newDueDate;
    }

    public void setNewDueDate(LocalDate newDueDate) {
        this.newDueDate = newDueDate;
    }

    public int getGrantedBy() {
        return grantedBy;
    }

    public void setGrantedBy(int grantedBy) {
        this.grantedBy = grantedBy;
    }

    public Timestamp getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(Timestamp grantedAt) {
        this.grantedAt = grantedAt;
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

    public String getBookTitle() {
        return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
        this.bookTitle = bookTitle;
    }

    public String getGrantedByName() {
        return grantedByName;
    }

    public void setGrantedByName(String grantedByName) {
        this.grantedByName = grantedByName;
    }
}
