package com.psgitech.library.model;

/**
 * Result DTO representing the outcome of a copy return or allocation release operation.
 */
public class AllocationResult {

    public enum Outcome {
        ALLOCATED,
        MADE_AVAILABLE
    }

    private Outcome outcome;
    private int copyId;
    private String copyCode;
    private AllocationRecord allocation;

    public AllocationResult() {}

    public AllocationResult(Outcome outcome, int copyId, String copyCode, AllocationRecord allocation) {
        this.outcome = outcome;
        this.copyId = copyId;
        this.copyCode = copyCode;
        this.allocation = allocation;
    }

    public static AllocationResult allocated(int copyId, String copyCode, AllocationRecord record) {
        return new AllocationResult(Outcome.ALLOCATED, copyId, copyCode, record);
    }

    public static AllocationResult madeAvailable(int copyId, String copyCode) {
        return new AllocationResult(Outcome.MADE_AVAILABLE, copyId, copyCode, null);
    }

    public boolean isAllocated() {
        return this.outcome == Outcome.ALLOCATED;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public void setOutcome(Outcome outcome) {
        this.outcome = outcome;
    }

    public int getCopyId() {
        return copyId;
    }

    public void setCopyId(int copyId) {
        this.copyId = copyId;
    }

    public String getCopyCode() {
        return copyCode;
    }

    public void setCopyCode(String copyCode) {
        this.copyCode = copyCode;
    }

    public AllocationRecord getAllocation() {
        return allocation;
    }

    public void setAllocation(AllocationRecord allocation) {
        this.allocation = allocation;
    }
}

