package com.psgitech.library.service;

import com.psgitech.library.model.AvailabilityInfo;
import com.psgitech.library.model.Book;
import com.psgitech.library.model.BookRecommendation;
import com.psgitech.library.model.RecommendationContext;
import com.psgitech.library.repository.DiscoveryRepository;
import com.psgitech.library.repository.RecommendationRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Personalized recommendation strategy mapping student's borrowing history and active reservations
 * to category and author interest profiles (Module 10.5, Decisions D6 and D15).
 */
public class CategoryBasedStrategy implements RecommendationStrategy {
    private final RecommendationRepository recommendationRepository;
    private final DiscoveryRepository discoveryRepository;

    public static final double WEIGHT_CATEGORY = 50.0;
    public static final double WEIGHT_AUTHOR = 30.0;
    public static final double WEIGHT_POPULARITY = 15.0;
    public static final double WEIGHT_RECENCY = 5.0;

    public CategoryBasedStrategy() {
        this.recommendationRepository = new RecommendationRepository();
        this.discoveryRepository = new DiscoveryRepository();
    }

    public CategoryBasedStrategy(RecommendationRepository recommendationRepository, DiscoveryRepository discoveryRepository) {
        this.recommendationRepository = recommendationRepository;
        this.discoveryRepository = discoveryRepository;
    }

    @Override
    public List<BookRecommendation> recommend(RecommendationContext context) {
        if (context.getStudentId() == null) {
            return Collections.emptyList();
        }

        int studentId = context.getStudentId();

        // 1. Extract student reading profile
        Map<String, Object> profile = recommendationRepository.getStudentReadingProfile(studentId);
        int historyCount = (Integer) profile.get("historyCount");
        if (historyCount < 2) {
            // Cold start handled by BookRecommendationService
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        Map<String, Double> categoryWeights = (Map<String, Double>) profile.get("categoryWeights");
        @SuppressWarnings("unchecked")
        Map<String, Double> authorWeights = (Map<String, Double>) profile.get("authorWeights");

        // Keep top 5 categories and authors
        Set<String> topCategories = getTopKeys(categoryWeights, 5);
        Set<String> topAuthors = getTopKeys(authorWeights, 5);

        double maxCatWeight = categoryWeights.values().stream().max(Double::compare).orElse(1.0);
        double maxAuthorWeight = authorWeights.values().stream().max(Double::compare).orElse(1.0);

        // 2. Retrieve student's excluded books
        Set<Integer> excludedBookIds = recommendationRepository.getExcludedBookIdsForStudent(studentId);

        // 3. Query candidate books (capped at 200)
        List<Book> candidates = recommendationRepository.getPersonalizedCandidates(
                topCategories, topAuthors, excludedBookIds, 200);

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 4. Score candidates
        long now = System.currentTimeMillis();
        long ninetyDaysMs = 90L * 24 * 60 * 60 * 1000;

        class ScoredCandidate {
            final Book book;
            final double score;
            final String reason;

            ScoredCandidate(Book book, double score, String reason) {
                this.book = book;
                this.score = score;
                this.reason = reason;
            }
        }

        List<ScoredCandidate> scoredList = new ArrayList<>();

        for (Book b : candidates) {
            double catScore = 0.0;
            String reason = null;
            if (b.getCategory() != null) {
                String catLower = b.getCategory().trim().toLowerCase();
                if (categoryWeights.containsKey(catLower)) {
                    catScore = (categoryWeights.get(catLower) / maxCatWeight) * WEIGHT_CATEGORY;
                    reason = "Because you borrowed books in " + b.getCategory();
                }
            }

            double authScore = 0.0;
            if (b.getAuthor() != null) {
                String authLower = b.getAuthor().trim().toLowerCase();
                if (authorWeights.containsKey(authLower)) {
                    authScore = (authorWeights.get(authLower) / maxAuthorWeight) * WEIGHT_AUTHOR;
                    if (reason == null) {
                        reason = "Because you read books by " + b.getAuthor();
                    }
                }
            }

            // Recency score (within last 90 days)
            double recencyScore = 0.0;
            if (b.getCreatedAt() != null && (now - b.getCreatedAt().getTime()) <= ninetyDaysMs) {
                recencyScore = WEIGHT_RECENCY;
            }

            // Popularity base score
            double popScore = 10.0;

            double total = catScore + authScore + recencyScore + popScore;
            // Normalize to 0 - 100
            double normalizedScore = Math.min(100.0, Math.round(total * 10.0) / 10.0);

            if (reason == null) {
                reason = "Recommended based on your reading interests";
            }

            scoredList.add(new ScoredCandidate(b, normalizedScore, reason));
        }

        // 5. Deterministic sorting: score DESC, title ASC, book_id ASC
        scoredList.sort((a, b) -> {
            int cmp = Double.compare(b.score, a.score);
            if (cmp != 0) return cmp;
            int titleCmp = a.book.getTitle().compareToIgnoreCase(b.book.getTitle());
            if (titleCmp != 0) return titleCmp;
            return Integer.compare(a.book.getBookId(), b.book.getBookId());
        });

        // 6. Select top 10
        int limit = Math.min(context.getLimit() > 0 ? context.getLimit() : 10, scoredList.size());
        List<ScoredCandidate> topPicks = scoredList.subList(0, limit);

        List<Integer> topIds = topPicks.stream().map(sc -> sc.book.getBookId()).collect(Collectors.toList());
        Map<Integer, AvailabilityInfo> availMap = discoveryRepository.getBatchedAvailability(topIds);

        List<BookRecommendation> recommendations = new ArrayList<>();
        for (ScoredCandidate sc : topPicks) {
            Book b = sc.book;
            AvailabilityInfo avail = availMap.get(b.getBookId());
            if (avail == null) {
                avail = new AvailabilityInfo(0, 0, 0, 0, 0, 0);
            }
            recommendations.add(new BookRecommendation(
                    b.getBookId(), b.getIsbn(), b.getTitle(), b.getAuthor(),
                    b.getPublisher(), b.getPublicationYear(), b.getCategory(),
                    b.getDepartment(), b.getDescription(), b.getCoverImageUrl(),
                    avail, sc.reason, "PERSONALIZED", sc.score
            ));
        }

        return recommendations;
    }

    private Set<String> getTopKeys(Map<String, Double> map, int topN) {
        if (map == null || map.isEmpty()) return Collections.emptySet();
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topN)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    @Override
    public String getStrategyName() {
        return "PERSONALIZED";
    }
}

