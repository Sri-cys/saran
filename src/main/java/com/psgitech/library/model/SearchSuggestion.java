package com.psgitech.library.model;

import java.util.Objects;

/**
 * Value object representing an autocomplete search suggestion item.
 * Types: TITLE, AUTHOR, CATEGORY.
 */
public class SearchSuggestion {
    private final String type;       // TITLE, AUTHOR, CATEGORY
    private final String text;       // Display text
    private final Integer bookId;    // Optional, populated for TITLE
    private final String filter;     // Optional, e.g. "category=Computer+Science"

    public SearchSuggestion(String type, String text, Integer bookId, String filter) {
        this.type = type;
        this.text = text != null ? text.trim() : "";
        this.bookId = bookId;
        this.filter = filter;
    }

    public static SearchSuggestion forTitle(String title, int bookId) {
        return new SearchSuggestion("TITLE", title, bookId, null);
    }

    public static SearchSuggestion forAuthor(String author) {
        return new SearchSuggestion("AUTHOR", author, null, "author=" + author);
    }

    public static SearchSuggestion forCategory(String category) {
        return new SearchSuggestion("CATEGORY", category, null, "category=" + category);
    }

    public String getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public Integer getBookId() {
        return bookId;
    }

    public String getFilter() {
        return filter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchSuggestion that = (SearchSuggestion) o;
        return Objects.equals(type, that.type) && Objects.equals(text.toLowerCase(), that.text.toLowerCase());
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, text.toLowerCase());
    }
}

