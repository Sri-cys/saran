package com.psgitech.library;

import com.psgitech.library.model.BookSearchCriteria;
import com.psgitech.library.repository.DiscoveryRepository;
import com.psgitech.library.repository.RecommendationRepository.PopularityRecord;
import com.psgitech.library.service.BookPopularityService;
import com.psgitech.library.util.AutocompleteRateLimiter;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Layer 1 Unit Tests for Smart Book Discovery (Feature 10).
 * Verifies criteria validation, whitespace collapsing, punctuation stripping,
 * LIKE wildcard escaping, rate limiting, and cache snapshot expiry.
 */
public class SmartDiscoveryUnitTest {

    @Test
    public void testCriteria_ValidQuery_NormalizedAndTermsExtracted() {
        BookSearchCriteria criteria = BookSearchCriteria.builder()
                .query("  Algorithms   Data   Structures  ")
                .category("Computer Science")
                .sort("RELEVANCE")
                .page(1)
                .pageSize(12)
                .build();

        assertEquals("Algorithms Data Structures", criteria.getNormalizedQuery());
        assertEquals(3, criteria.getSearchTerms().size());
        assertEquals("Algorithms", criteria.getSearchTerms().get(0));
        assertEquals("Data", criteria.getSearchTerms().get(1));
        assertEquals("Structures", criteria.getSearchTerms().get(2));
        assertFalse(criteria.isExtraTermsIgnored());
        assertTrue(criteria.hasQuery());
    }

    @Test
    public void testCriteria_PunctuationOnly_TreatedAsEmptyBrowseMode() {
        BookSearchCriteria criteria = BookSearchCriteria.builder()
                .query("  !@#$%^&*()_+-=~`  ")
                .build();

        assertFalse(criteria.hasQuery());
        assertEquals("", criteria.getNormalizedQuery());
        assertTrue(criteria.getSearchTerms().isEmpty());
        assertEquals("RECENTLY_ADDED", criteria.getSort());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCriteria_OverLengthQuery_ThrowsException() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 105; i++) {
            sb.append("a");
        }
        BookSearchCriteria.builder().query(sb.toString()).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCriteria_OverLengthSingleTerm_ThrowsException() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 55; i++) {
            sb.append("x");
        }
        BookSearchCriteria.builder().query(sb.toString()).build();
    }

    @Test
    public void testCriteria_ExtraTermsBeyondFive_IgnoredWithFlag() {
        BookSearchCriteria criteria = BookSearchCriteria.builder()
                .query("one two three four five six seven")
                .build();

        assertEquals(5, criteria.getSearchTerms().size());
        assertTrue(criteria.isExtraTermsIgnored());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCriteria_InvalidSort_ThrowsException() {
        BookSearchCriteria.builder().sort("INVALID_SORT_OPTION").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCriteria_InvalidAvailability_ThrowsException() {
        BookSearchCriteria.builder().availability("UNKNOWN_STATUS").build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCriteria_YearFromGreaterThanYearTo_ThrowsException() {
        BookSearchCriteria.builder().yearFrom(2025).yearTo(2020).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCriteria_PageSizeExceedsMaximum_ThrowsException() {
        BookSearchCriteria.builder().pageSize(100).build();
    }

    @Test
    public void testLikeWildcardEscaping() {
        String input = "100%_pure!code";
        String escaped = DiscoveryRepository.escapeLike(input);
        // '!' becomes '!!', '%' becomes '!%', '_' becomes '!_'
        assertEquals("100!%!_pure!!code", escaped);
    }

    @Test
    public void testAutocompleteRateLimiter_EnforcesLimitAndEvicts() {
        AutocompleteRateLimiter limiter = AutocompleteRateLimiter.getInstance();
        limiter.reset();

        String client = "test-session-123";
        for (int i = 1; i <= AutocompleteRateLimiter.MAX_REQUESTS; i++) {
            assertTrue("Request " + i + " should be allowed", limiter.allowRequest(client));
        }

        // 21st request should be rejected (HTTP 429)
        assertFalse("Request beyond threshold must be rejected", limiter.allowRequest(client));

        // Different client should still be admitted
        assertTrue("Different client should be allowed", limiter.allowRequest("test-session-456"));
    }

    @Test
    public void testCachedPopularitySnapshot_Expiry() throws InterruptedException {
        long now = System.currentTimeMillis();
        List<PopularityRecord> sample = Collections.singletonList(new PopularityRecord(1, 10.0, 3, false));

        BookPopularityService.CachedPopularity cache = new BookPopularityService.CachedPopularity(now, sample);
        assertFalse(cache.isExpired());
        assertEquals(1, cache.getRecords().size());

        // Snapshot initialized with timestamp in the past
        BookPopularityService.CachedPopularity expiredCache =
                new BookPopularityService.CachedPopularity(now - (BookPopularityService.CACHE_TTL_MS + 1000L), sample);
        assertTrue(expiredCache.isExpired());
    }
}
