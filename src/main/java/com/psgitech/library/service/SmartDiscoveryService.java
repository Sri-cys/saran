package com.psgitech.library.service;

import com.psgitech.library.exception.RateLimitExceededException;
import com.psgitech.library.model.Book;
import com.psgitech.library.model.BookSearchCriteria;
import com.psgitech.library.model.DiscoveryCategory;
import com.psgitech.library.model.PagedResult;
import com.psgitech.library.model.SearchSuggestion;
import com.psgitech.library.repository.DiscoveryRepository;
import com.psgitech.library.util.AutocompleteRateLimiter;

import java.util.List;

/**
 * Service managing smart book discovery, parameterized catalog searching,
 * rate-limited autocomplete suggestions, and category facets.
 */
public class SmartDiscoveryService {
    private final DiscoveryRepository discoveryRepository;
    private final AutocompleteRateLimiter rateLimiter;

    public SmartDiscoveryService() {
        this.discoveryRepository = new DiscoveryRepository();
        this.rateLimiter = AutocompleteRateLimiter.getInstance();
    }

    public SmartDiscoveryService(DiscoveryRepository discoveryRepository, AutocompleteRateLimiter rateLimiter) {
        this.discoveryRepository = discoveryRepository;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Executes smart search and advanced filtering over the active catalog.
     */
    public PagedResult<Book> search(BookSearchCriteria criteria) {
        return discoveryRepository.searchBooks(criteria);
    }

    /**
     * Retrieves autocomplete suggestions with rate limit enforcement (Decision D11).
     * Limit: 20 requests per 10 seconds per client.
     */
    public List<SearchSuggestion> getSuggestions(String query, String clientId) {
        if (!rateLimiter.allowRequest(clientId)) {
            throw new RateLimitExceededException("Autocomplete request rate limit exceeded. Max 20 requests per 10 seconds.");
        }
        return discoveryRepository.getAutocompleteSuggestions(query);
    }

    /**
     * Retrieves normalized categories with book and copy counts.
     */
    public List<DiscoveryCategory> getCategories() {
        return discoveryRepository.getNormalizedCategories();
    }
}
