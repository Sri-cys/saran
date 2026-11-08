package com.psgitech.library.model;

/**
 * Value object representing a normalized catalog category with book count
 * and currently available physical copy count.
 */
public class DiscoveryCategory implements Comparable<DiscoveryCategory> {
    private final String normalizedName;
    private final String displayName;
    private final int bookCount;
    private final int availableCopyCount;

    public DiscoveryCategory(String normalizedName, String displayName, int bookCount, int availableCopyCount) {
        this.normalizedName = normalizedName;
        this.displayName = displayName;
        this.bookCount = bookCount;
        this.availableCopyCount = availableCopyCount;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getBookCount() {
        return bookCount;
    }

    public int getAvailableCopyCount() {
        return availableCopyCount;
    }

    @Override
    public int compareTo(DiscoveryCategory other) {
        if (other == null) return 1;
        return this.displayName.compareToIgnoreCase(other.displayName);
    }
}
