package com.psgitech.library.service;

import com.psgitech.library.model.AvailabilityInfo;
import com.psgitech.library.model.Book;
import com.psgitech.library.model.BookRecommendation;
import com.psgitech.library.repository.DiscoveryRepository;
import com.psgitech.library.repository.RecommendationRepository;
import com.psgitech.library.repository.RecommendationRepository.PopularityRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service managing global popularity and trending books computation, in-memory caching,
 * and live availability hydration (Decisions D4 and D5).
 */
public class BookPopularityService {
    private static final Logger LOGGER = Logger.getLogger(BookPopularityService.class.getName());
    public static final long CACHE_TTL_MS = 300_000L; // 300 seconds (5 minutes)

    /**
     * Immutable snapshot container for cached popularity rankings.
     */
    public static class CachedPopularity {
        private final long cachedAt;
        private final List<PopularityRecord> records;

        public CachedPopularity(long cachedAt, List<PopularityRecord> records) {
            this.cachedAt = cachedAt;
            this.records = records != null ? Collections.unmodifiableList(records) : Collections.emptyList();
        }

        public boolean isExpired() {
            return (System.currentTimeMillis() - cachedAt) > CACHE_TTL_MS;
        }

        public List<PopularityRecord> getRecords() {
            return records;
        }

        public long getCachedAt() {
            return cachedAt;
        }
    }

    private static volatile CachedPopularity cachedPopular;
    private static volatile CachedPopularity cachedTrending;
    private static final AtomicBoolean isRefreshingPopular = new AtomicBoolean(false);
    private static final AtomicBoolean isRefreshingTrending = new AtomicBoolean(false);

    private final RecommendationRepository recommendationRepository;
    private final DiscoveryRepository discoveryRepository;

    public BookPopularityService() {
        this.recommendationRepository = new RecommendationRepository();
        this.discoveryRepository = new DiscoveryRepository();
    }

    public BookPopularityService(RecommendationRepository recommendationRepository, DiscoveryRepository discoveryRepository) {
        this.recommendationRepository = recommendationRepository;
        this.discoveryRepository = discoveryRepository;
    }

    /**
     * Retrieves top popular books (90-day window) with live availability.
     */
    public List<BookRecommendation> getPopularBooks(int limit) {
        return getRankedBooks(90, limit, "popular");
    }

    /**
     * Retrieves top trending books (30-day window) with live availability.
     */
    public List<BookRecommendation> getTrendingBooks(int limit) {
        return getRankedBooks(30, limit, "trending");
    }

    /**
     * Fetches popularity ranking with thread-safe caching and live availability attachment.
     */
    private List<BookRecommendation> getRankedBooks(int daysWindow, int limit, String type) {
        boolean isTrending = "trending".equalsIgnoreCase(type);
        CachedPopularity cache = isTrending ? cachedTrending : cachedPopular;
        AtomicBoolean refresher = isTrending ? isRefreshingTrending : isRefreshingPopular;

        List<PopularityRecord> records;

        if (cache != null && !cache.isExpired()) {
            records = cache.getRecords();
        } else {
            // Need refresh
            if (refresher.compareAndSet(false, true)) {
                try {
                    List<PopularityRecord> fresh = recommendationRepository.getPopularityRanking(daysWindow, 10);
                    CachedPopularity newCache = new CachedPopularity(System.currentTimeMillis(), fresh);
                    if (isTrending) {
                        cachedTrending = newCache;
                    } else {
                        cachedPopular = newCache;
                    }
                    records = fresh;
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error refreshing popularity ranking for " + type, e);
                    // If stale cache exists, serve stale on failure
                    records = (cache != null) ? cache.getRecords() : Collections.emptyList();
                } finally {
                    refresher.set(false);
                }
            } else {
                // Another thread is refreshing; return existing cache if present, else wait briefly
                records = (cache != null) ? cache.getRecords() : recommendationRepository.getPopularityRanking(daysWindow, 10);
            }
        }

        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        // Apply requested limit (capped at max 10)
        int cappedLimit = Math.min(limit > 0 ? limit : 10, records.size());
        List<PopularityRecord> subset = records.subList(0, cappedLimit);

        List<Integer> bookIds = new ArrayList<>();
        for (PopularityRecord r : subset) {
            bookIds.add(r.getBookId());
        }

        // Hydrate books and live physical copy availability
        List<Book> books = recommendationRepository.findBooksByIds(bookIds);
        Map<Integer, AvailabilityInfo> availMap = discoveryRepository.getBatchedAvailability(bookIds);

        Map<Integer, Book> bookMap = new java.util.HashMap<>();
        for (Book b : books) {
            bookMap.put(b.getBookId(), b);
        }

        List<BookRecommendation> recommendations = new ArrayList<>();
        for (PopularityRecord r : subset) {
            Book b = bookMap.get(r.getBookId());
            if (b != null) {
                AvailabilityInfo avail = availMap.get(b.getBookId());
                if (avail == null) {
                    avail = new AvailabilityInfo(0, 0, 0, 0, 0, 0);
                }

                String label;
                if (r.isFallback()) {
                    label = "New arrival";
                } else if (isTrending) {
                    label = "Trending";
                } else {
                    label = "Popular";
                }

                recommendations.add(new BookRecommendation(
                        b.getBookId(), b.getIsbn(), b.getTitle(), b.getAuthor(),
                        b.getPublisher(), b.getPublicationYear(), b.getCategory(),
                        b.getDepartment(), b.getDescription(), b.getCoverImageUrl(),
                        avail, label, isTrending ? "TRENDING" : "POPULAR", r.getScore()
                ));
            }
        }

        return recommendations;
    }

    /**
     * Visible for testing: invalidates the in-memory cache.
     */
    public static void clearCache() {
        cachedPopular = null;
        cachedTrending = null;
    }
}
