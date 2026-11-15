package com.psgitech.library.model;

import com.psgitech.library.policy.StandardLoanPolicy;

import java.sql.Timestamp;

/**
 * Model representing the per-book persistent dynamic policy state in book_loan_policy_state.
 * Required to support hysteresis across evaluation cycles.
 */
public class BookPolicyState {
    private int bookId;
    private String policyCode;
    private Timestamp enteredAt;
    private Timestamp lastEvaluatedAt;
    private int lastDemand;

    public BookPolicyState() {
        this.policyCode = StandardLoanPolicy.CODE;
    }

    public BookPolicyState(int bookId, String policyCode, Timestamp enteredAt, Timestamp lastEvaluatedAt, int lastDemand) {
        this.bookId = bookId;
        this.policyCode = policyCode != null ? policyCode : StandardLoanPolicy.CODE;
        this.enteredAt = enteredAt;
        this.lastEvaluatedAt = lastEvaluatedAt;
        this.lastDemand = lastDemand;
    }

    public int getBookId() {
        return bookId;
    }

    public void setBookId(int bookId) {
        this.bookId = bookId;
    }

    public String getPolicyCode() {
        return policyCode;
    }

    public void setPolicyCode(String policyCode) {
        this.policyCode = policyCode;
    }

    public Timestamp getEnteredAt() {
        return enteredAt;
    }

    public void setEnteredAt(Timestamp enteredAt) {
        this.enteredAt = enteredAt;
    }

    public Timestamp getLastEvaluatedAt() {
        return lastEvaluatedAt;
    }

    public void setLastEvaluatedAt(Timestamp lastEvaluatedAt) {
        this.lastEvaluatedAt = lastEvaluatedAt;
    }

    public int getLastDemand() {
        return lastDemand;
    }

    public void setLastDemand(int lastDemand) {
        this.lastDemand = lastDemand;
    }

    @Override
    public String toString() {
        return "BookPolicyState{" +
                "bookId=" + bookId +
                ", policyCode='" + policyCode + '\'' +
                ", lastDemand=" + lastDemand +
                ", enteredAt=" + enteredAt +
                '}';
    }
}
