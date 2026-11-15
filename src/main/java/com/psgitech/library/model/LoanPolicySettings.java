package com.psgitech.library.model;

import com.psgitech.library.config.BorrowingPolicy;

import java.sql.Timestamp;
import java.util.Objects;

/**
 * Immutable value object representing institutional dynamic loan policy configuration settings.
 * Enforces domain validation rules (hysteresis thresholds, loan period bounds).
 */
public final class LoanPolicySettings {

    public static final int DEFAULT_ENTER_THRESHOLD = 4;
    public static final int DEFAULT_EXIT_THRESHOLD = 1;
    public static final int DEFAULT_HIGH_DEMAND_DAYS = 5;

    private final boolean enabled;
    private final int enterThreshold;
    private final int exitThreshold;
    private final int highDemandDays;
    private final int version;
    private final Integer updatedBy;
    private final Timestamp updatedAt;

    public LoanPolicySettings(boolean enabled, int enterThreshold, int exitThreshold, int highDemandDays,
                              int version, Integer updatedBy, Timestamp updatedAt) {
        validate(enterThreshold, exitThreshold, highDemandDays, version);
        this.enabled = enabled;
        this.enterThreshold = enterThreshold;
        this.exitThreshold = exitThreshold;
        this.highDemandDays = highDemandDays;
        this.version = version;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt != null ? new Timestamp(updatedAt.getTime()) : null;
    }

    public static LoanPolicySettings defaultSettings() {
        return new LoanPolicySettings(
                true,
                DEFAULT_ENTER_THRESHOLD,
                DEFAULT_EXIT_THRESHOLD,
                DEFAULT_HIGH_DEMAND_DAYS,
                1,
                null,
                new Timestamp(System.currentTimeMillis())
        );
    }

    public static void validate(int enterThreshold, int exitThreshold, int highDemandDays, int version) {
        if (enterThreshold < 2 || enterThreshold > 100) {
            throw new IllegalArgumentException("Enter threshold must be between 2 and 100 waiting students.");
        }
        if (exitThreshold < 0 || exitThreshold > 100) {
            throw new IllegalArgumentException("Exit threshold must be between 0 and 100 waiting students.");
        }
        if (exitThreshold >= enterThreshold) {
            throw new IllegalArgumentException("Hysteresis violation: Exit threshold (" + exitThreshold +
                    ") must be strictly less than Enter threshold (" + enterThreshold + ").");
        }
        int standardDays = BorrowingPolicy.DEFAULT_BORROWING_DAYS;
        if (highDemandDays < 2 || highDemandDays >= standardDays) {
            throw new IllegalArgumentException("High-demand loan days must be at least 2 and strictly less than the standard loan period (" + standardDays + " days). Received: " + highDemandDays);
        }
        if (version < 1) {
            throw new IllegalArgumentException("Settings version must be at least 1.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getEnterThreshold() {
        return enterThreshold;
    }

    public int getExitThreshold() {
        return exitThreshold;
    }

    public int getHighDemandDays() {
        return highDemandDays;
    }

    public int getVersion() {
        return version;
    }

    public Integer getUpdatedBy() {
        return updatedBy;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt != null ? new Timestamp(updatedAt.getTime()) : null;
    }

    /**
     * Creates a new instance with an active exam window enter-threshold override.
     */
    public LoanPolicySettings withEnterThresholdOverride(int overrideThreshold) {
        if (overrideThreshold < 2 || overrideThreshold >= this.enterThreshold) {
            return this;
        }
        return new LoanPolicySettings(
                this.enabled,
                overrideThreshold,
                this.exitThreshold,
                this.highDemandDays,
                this.version,
                this.updatedBy,
                this.updatedAt
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LoanPolicySettings that = (LoanPolicySettings) o;
        return enabled == that.enabled &&
                enterThreshold == that.enterThreshold &&
                exitThreshold == that.exitThreshold &&
                highDemandDays == that.highDemandDays &&
                version == that.version;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, enterThreshold, exitThreshold, highDemandDays, version);
    }

    @Override
    public String toString() {
        return "LoanPolicySettings{" +
                "enabled=" + enabled +
                ", enterThreshold=" + enterThreshold +
                ", exitThreshold=" + exitThreshold +
                ", highDemandDays=" + highDemandDays +
                ", version=" + version +
                '}';
    }
}
