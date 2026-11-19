package com.psgitech.library.dto.librarian;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * DTO encapsulating a student's computed Trust Standing tier and contributing factors.
 */
public class TrustStandingDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Tier {
        EXEMPLARY,
        STANDARD,
        WATCHLIST,
        RESTRICTED
    }

    private int studentId;
    private String studentName;
    private String registerNumber;
    private Tier tier;
    private double onTimeRatePercent;
    private int totalReturns12m;
    private int onTimeReturns12m;
    private int activeOverdueCount;
    private BigDecimal unpaidFinesAmount;
    private int oldestUnpaidFineDays;
    private int lostOrDamagedCount;
    private String explanation;
    private boolean eligibleForGoodwill;
    private boolean goodwillAlreadyUsed;

    public TrustStandingDTO() {
        this.tier = Tier.STANDARD;
        this.unpaidFinesAmount = BigDecimal.ZERO;
    }

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getRegisterNumber() { return registerNumber; }
    public void setRegisterNumber(String registerNumber) { this.registerNumber = registerNumber; }

    public Tier getTier() { return tier; }
    public void setTier(Tier tier) { this.tier = tier; }

    public double getOnTimeRatePercent() { return onTimeRatePercent; }
    public void setOnTimeRatePercent(double onTimeRatePercent) { this.onTimeRatePercent = onTimeRatePercent; }

    public int getTotalReturns12m() { return totalReturns12m; }
    public void setTotalReturns12m(int totalReturns12m) { this.totalReturns12m = totalReturns12m; }

    public int getOnTimeReturns12m() { return onTimeReturns12m; }
    public void setOnTimeReturns12m(int onTimeReturns12m) { this.onTimeReturns12m = onTimeReturns12m; }

    public int getActiveOverdueCount() { return activeOverdueCount; }
    public void setActiveOverdueCount(int activeOverdueCount) { this.activeOverdueCount = activeOverdueCount; }

    public BigDecimal getUnpaidFinesAmount() { return unpaidFinesAmount; }
    public void setUnpaidFinesAmount(BigDecimal unpaidFinesAmount) { this.unpaidFinesAmount = unpaidFinesAmount; }

    public int getOldestUnpaidFineDays() { return oldestUnpaidFineDays; }
    public void setOldestUnpaidFineDays(int oldestUnpaidFineDays) { this.oldestUnpaidFineDays = oldestUnpaidFineDays; }

    public int getLostOrDamagedCount() { return lostOrDamagedCount; }
    public void setLostOrDamagedCount(int lostOrDamagedCount) { this.lostOrDamagedCount = lostOrDamagedCount; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public boolean isEligibleForGoodwill() { return eligibleForGoodwill; }
    public void setEligibleForGoodwill(boolean eligibleForGoodwill) { this.eligibleForGoodwill = eligibleForGoodwill; }

    public boolean isGoodwillAlreadyUsed() { return goodwillAlreadyUsed; }
    public void setGoodwillAlreadyUsed(boolean goodwillAlreadyUsed) { this.goodwillAlreadyUsed = goodwillAlreadyUsed; }
}
