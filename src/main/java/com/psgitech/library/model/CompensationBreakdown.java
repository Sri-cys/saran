package com.psgitech.library.model;

import com.psgitech.library.enums.CaseType;
import com.psgitech.library.enums.DamageSeverity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Immutable value object holding all inputs, parameters, and results of a compensation calculation.
 * Preserved as an audit snapshot in loss_damage_cases so amounts remain explainable.
 */
public final class CompensationBreakdown {
    private final CaseType caseType;
    private final BigDecimal priceUsed;
    private final int ageDays;
    private final boolean ageFlag;
    private final BigDecimal depreciationApplied;
    private final BigDecimal processingFee;
    private final DamageSeverity severity;
    private final BigDecimal severityFraction;
    private final BigDecimal computedAmount;

    public CompensationBreakdown(CaseType caseType, BigDecimal priceUsed, int ageDays, boolean ageFlag,
                                 BigDecimal depreciationApplied, BigDecimal processingFee,
                                 DamageSeverity severity, BigDecimal severityFraction,
                                 BigDecimal computedAmount) {
        this.caseType = caseType;
        this.priceUsed = priceUsed != null ? priceUsed.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.ageDays = ageDays;
        this.ageFlag = ageFlag;
        this.depreciationApplied = depreciationApplied != null ? depreciationApplied.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        this.processingFee = processingFee != null ? processingFee.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.severity = severity;
        this.severityFraction = severityFraction != null ? severityFraction.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
        this.computedAmount = computedAmount != null ? computedAmount.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public CaseType getCaseType() { return caseType; }
    public BigDecimal getPriceUsed() { return priceUsed; }
    public int getAgeDays() { return ageDays; }
    public boolean isAgeFlag() { return ageFlag; }
    public BigDecimal getDepreciationApplied() { return depreciationApplied; }
    public BigDecimal getProcessingFee() { return processingFee; }
    public DamageSeverity getSeverity() { return severity; }
    public BigDecimal getSeverityFraction() { return severityFraction; }
    public BigDecimal getComputedAmount() { return computedAmount; }
    public BigDecimal getFinalAmount() { return computedAmount; }

    @Override
    public String toString() {
        return "CompensationBreakdown{" +
                "caseType=" + caseType +
                ", priceUsed=" + priceUsed +
                ", ageDays=" + ageDays +
                ", ageFlag=" + ageFlag +
                ", depreciationApplied=" + depreciationApplied +
                ", processingFee=" + processingFee +
                ", severity=" + severity +
                ", severityFraction=" + severityFraction +
                ", computedAmount=" + computedAmount +
                '}';
    }
}
