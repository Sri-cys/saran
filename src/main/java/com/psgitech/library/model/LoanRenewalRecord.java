package com.psgitech.library.model;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;

public class LoanRenewalRecord {
    private int renewalId;
    private int transactionId;
    private int studentId;
    private LocalDate previousDueDate;
    private LocalDate newDueDate;
    private int renewalNumber;
    private Timestamp renewedAt;
    private int renewedBy;

    public LoanRenewalRecord() {}

    public LoanRenewalRecord(int transactionId, int studentId, LocalDate previousDueDate,
                             LocalDate newDueDate, int renewalNumber, int renewedBy) {
        this.transactionId = transactionId;
        this.studentId = studentId;
        this.previousDueDate = previousDueDate;
        this.newDueDate = newDueDate;
        this.renewalNumber = renewalNumber;
        this.renewedBy = renewedBy;
    }

    public int getRenewalId() {
        return renewalId;
    }

    public void setRenewalId(int renewalId) {
        this.renewalId = renewalId;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(int transactionId) {
        this.transactionId = transactionId;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
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

    public int getRenewalNumber() {
        return renewalNumber;
    }

    public void setRenewalNumber(int renewalNumber) {
        this.renewalNumber = renewalNumber;
    }

    public Timestamp getRenewedAt() {
        return renewedAt;
    }

    public void setRenewedAt(Timestamp renewedAt) {
        this.renewedAt = renewedAt;
    }

    public int getRenewedBy() {
        return renewedBy;
    }

    public void setRenewedBy(int renewedBy) {
        this.renewedBy = renewedBy;
    }
}
