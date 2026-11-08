package com.psgitech.library.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Validated, immutable search criteria object for smart book discovery and filtering.
 * Enforces input constraints (max length 100, max 5 terms of max 50 chars, page size <= 50).
 */
public class BookSearchCriteria {
    public static final int MAX_QUERY_LENGTH = 100;
    public static final int MAX_TERM_LENGTH = 50;
    public static final int MAX_TERMS = 5;
    public static final int DEFAULT_PAGE_SIZE = 12;
    public static final int MAX_PAGE_SIZE = 50;

    private final String rawQuery;
    private final String normalizedQuery;
    private final List<String> searchTerms;
    private final boolean extraTermsIgnored;
    private final String category;
    private final String author;
    private final String publisher;
    private final String department;
    private final Integer yearFrom;
    private final Integer yearTo;
    private final String availability; // ALL, AVAILABLE, UNAVAILABLE, WAITING
    private final String sort;         // RELEVANCE, RECENTLY_ADDED, YEAR_DESC, YEAR_ASC, POPULARITY, TITLE_ASC
    private final int page;
    private final int pageSize;

    private BookSearchCriteria(Builder builder) {
        this.rawQuery = builder.rawQuery;
        this.normalizedQuery = builder.normalizedQuery;
        this.searchTerms = Collections.unmodifiableList(builder.searchTerms);
        this.extraTermsIgnored = builder.extraTermsIgnored;
        this.category = builder.category;
        this.author = builder.author;
        this.publisher = builder.publisher;
        this.department = builder.department;
        this.yearFrom = builder.yearFrom;
        this.yearTo = builder.yearTo;
        this.availability = builder.availability;
        this.sort = builder.sort;
        this.page = builder.page;
        this.pageSize = builder.pageSize;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getRawQuery() {
        return rawQuery;
    }

    public String getNormalizedQuery() {
        return normalizedQuery;
    }

    public List<String> getSearchTerms() {
        return searchTerms;
    }

    public boolean isExtraTermsIgnored() {
        return extraTermsIgnored;
    }

    public boolean hasQuery() {
        return !searchTerms.isEmpty();
    }

    public String getCategory() {
        return category;
    }

    public String getAuthor() {
        return author;
    }

    public String getPublisher() {
        return publisher;
    }

    public String getDepartment() {
        return department;
    }

    public Integer getYearFrom() {
        return yearFrom;
    }

    public Integer getYearTo() {
        return yearTo;
    }

    public String getAvailability() {
        return availability;
    }

    public String getSort() {
        return sort;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getOffset() {
        return (page - 1) * pageSize;
    }

    public static class Builder {
        private String rawQuery;
        private String normalizedQuery = "";
        private List<String> searchTerms = new ArrayList<>();
        private boolean extraTermsIgnored = false;
        private String category;
        private String author;
        private String publisher;
        private String department;
        private Integer yearFrom;
        private Integer yearTo;
        private String availability = "ALL";
        private String sort;
        private int page = 1;
        private int pageSize = DEFAULT_PAGE_SIZE;

        public Builder query(String q) {
            this.rawQuery = q;
            if (q == null) return this;

            if (q.length() > MAX_QUERY_LENGTH) {
                throw new IllegalArgumentException("Search query exceeds maximum length of " + MAX_QUERY_LENGTH + " characters.");
            }

            // Strip control characters
            String cleaned = q.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ").trim();

            // Check if string is only punctuation
            if (cleaned.replaceAll("[\\p{Punct}\\s]", "").isEmpty()) {
                // Punctuation only -> treated as empty query (browse mode)
                this.normalizedQuery = "";
                this.searchTerms = new ArrayList<>();
                return this;
            }

            // Normalize whitespace
            cleaned = cleaned.replaceAll("\\s+", " ").trim();
            this.normalizedQuery = cleaned;

            String[] rawTerms = cleaned.split(" ");
            List<String> terms = new ArrayList<>();
            for (String t : rawTerms) {
                if (t.isEmpty()) continue;
                if (t.length() > MAX_TERM_LENGTH) {
                    throw new IllegalArgumentException("Search term '" + t + "' exceeds maximum length of " + MAX_TERM_LENGTH + " characters.");
                }
                if (terms.size() < MAX_TERMS) {
                    terms.add(t);
                } else {
                    this.extraTermsIgnored = true;
                }
            }
            this.searchTerms = terms;
            return this;
        }

        public Builder category(String cat) {
            this.category = (cat != null && !cat.trim().isEmpty()) ? cat.trim() : null;
            return this;
        }

        public Builder author(String aut) {
            this.author = (aut != null && !aut.trim().isEmpty()) ? aut.trim() : null;
            return this;
        }

        public Builder publisher(String pub) {
            this.publisher = (pub != null && !pub.trim().isEmpty()) ? pub.trim() : null;
            return this;
        }

        public Builder department(String dept) {
            this.department = (dept != null && !dept.trim().isEmpty()) ? dept.trim() : null;
            return this;
        }

        public Builder yearFrom(Integer yf) {
            if (yf != null && yf <= 0) {
                throw new IllegalArgumentException("yearFrom must be a positive integer.");
            }
            this.yearFrom = yf;
            return this;
        }

        public Builder yearTo(Integer yt) {
            if (yt != null && yt <= 0) {
                throw new IllegalArgumentException("yearTo must be a positive integer.");
            }
            this.yearTo = yt;
            return this;
        }

        public Builder availability(String avail) {
            if (avail == null || avail.trim().isEmpty()) {
                this.availability = "ALL";
            } else {
                String upper = avail.trim().toUpperCase();
                if (!upper.equals("ALL") && !upper.equals("AVAILABLE") && !upper.equals("UNAVAILABLE") && !upper.equals("WAITING")) {
                    throw new IllegalArgumentException("Invalid availability filter '" + avail + "'. Must be ALL, AVAILABLE, UNAVAILABLE, or WAITING.");
                }
                this.availability = upper;
            }
            return this;
        }

        public Builder sort(String s) {
            if (s == null || s.trim().isEmpty()) {
                this.sort = null; // Will default in build()
            } else {
                String upper = s.trim().toUpperCase();
                switch (upper) {
                    case "RELEVANCE":
                    case "RECENTLY_ADDED":
                    case "YEAR_DESC":
                    case "YEAR_ASC":
                    case "POPULARITY":
                    case "TITLE_ASC":
                        this.sort = upper;
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid sort option '" + s + "'. Must be RELEVANCE, RECENTLY_ADDED, YEAR_DESC, YEAR_ASC, POPULARITY, or TITLE_ASC.");
                }
            }
            return this;
        }

        public Builder page(Integer p) {
            if (p != null) {
                if (p < 1) {
                    throw new IllegalArgumentException("Page number must be greater than or equal to 1.");
                }
                this.page = p;
            }
            return this;
        }

        public Builder pageSize(Integer ps) {
            if (ps != null) {
                if (ps < 1) {
                    throw new IllegalArgumentException("Page size must be greater than or equal to 1.");
                }
                if (ps > MAX_PAGE_SIZE) {
                    throw new IllegalArgumentException("Page size cannot exceed " + MAX_PAGE_SIZE + ".");
                }
                this.pageSize = ps;
            }
            return this;
        }

        public BookSearchCriteria build() {
            // Apply default sort if not explicitly specified
            if (this.sort == null) {
                this.sort = (!this.searchTerms.isEmpty()) ? "RELEVANCE" : "RECENTLY_ADDED";
            }
            if (this.yearFrom != null && this.yearTo != null && this.yearFrom > this.yearTo) {
                throw new IllegalArgumentException("yearFrom (" + this.yearFrom + ") cannot be greater than yearTo (" + this.yearTo + ").");
            }
            return new BookSearchCriteria(this);
        }
    }
}
