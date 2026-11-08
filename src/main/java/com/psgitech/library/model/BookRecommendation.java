package com.psgitech.library.model;

import java.time.Instant;

/**
 * Value object representing a recommended, trending, popular, or similar book.
 * Contains book bibliographic summary, live physical availability, recommendation reason/label,
 * score, and timestamp.
 */
public class BookRecommendation {
    private final int bookId;
    private final String isbn;
    private final String title;
    private final String author;
    private final String publisher;
    private final Integer publicationYear;
    private final String category;
    private final String department;
    private final String description;
    private final String coverImageUrl;
    private final AvailabilityInfo availability;
    private final String label;        // e.g. "Popular", "Trending", "New arrival", or recommendation reason
    private final String strategy;     // "PERSONALIZED", "DEPARTMENT", "POPULAR_FALLBACK", "SIMILAR"
    private final double score;        // 0 - 100
    private final String asOf;         // Server ISO timestamp

    public BookRecommendation(int bookId, String isbn, String title, String author, String publisher,
                              Integer publicationYear, String category, String department, String description,
                              String coverImageUrl, AvailabilityInfo availability, String label,
                              String strategy, double score) {
        this.bookId = bookId;
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.publicationYear = publicationYear;
        this.category = category;
        this.department = department;
        this.description = description;
        this.coverImageUrl = coverImageUrl;
        this.availability = availability;
        this.label = label;
        this.strategy = strategy;
        this.score = score;
        this.asOf = Instant.now().toString();
    }

    public int getBookId() {
        return bookId;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getPublisher() {
        return publisher;
    }

    public Integer getPublicationYear() {
        return publicationYear;
    }

    public String getCategory() {
        return category;
    }

    public String getDepartment() {
        return department;
    }

    public String getDescription() {
        return description;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public AvailabilityInfo getAvailability() {
        return availability;
    }

    public String getLabel() {
        return label;
    }

    public String getStrategy() {
        return strategy;
    }

    public double getScore() {
        return score;
    }

    public String getAsOf() {
        return asOf;
    }
}

