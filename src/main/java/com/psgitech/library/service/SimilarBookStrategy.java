package com.psgitech.library.service;

import com.psgitech.library.model.AvailabilityInfo;
import com.psgitech.library.model.Book;
import com.psgitech.library.model.BookRecommendation;
import com.psgitech.library.model.RecommendationContext;
import com.psgitech.library.repository.DiscoveryRepository;
import com.psgitech.library.repository.RecommendationRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Recommendation strategy serving content-based similar books for a target book (Module 10.6, Decision D15).
 */
public class SimilarBookStrategy implements RecommendationStrategy {
    private final RecommendationRepository recommendationRepository;
    private final DiscoveryRepository discoveryRepository;

    public SimilarBookStrategy() {
        this.recommendationRepository = new RecommendationRepository();
        this.discoveryRepository = new DiscoveryRepository();
    }

    public SimilarBookStrategy(RecommendationRepository recommendationRepository, DiscoveryRepository discoveryRepository) {
        this.recommendationRepository = recommendationRepository;
        this.discoveryRepository = discoveryRepository;
    }

    @Override
    public List<BookRecommendation> recommend(RecommendationContext context) {
        if (context.getTargetBookId() == null) {
            return Collections.emptyList();
        }

        int limit = Math.min(context.getLimit() > 0 ? context.getLimit() : 6, 6);
        List<Book> similarBooks = recommendationRepository.getSimilarBooks(context.getTargetBookId(), limit);
        if (similarBooks.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> ids = new ArrayList<>();
        for (Book b : similarBooks) {
            ids.add(b.getBookId());
        }

        Map<Integer, AvailabilityInfo> availMap = discoveryRepository.getBatchedAvailability(ids);
        List<BookRecommendation> recommendations = new ArrayList<>();

        for (Book b : similarBooks) {
            AvailabilityInfo avail = availMap.get(b.getBookId());
            if (avail == null) {
                avail = new AvailabilityInfo(0, 0, 0, 0, 0, 0);
            }
            recommendations.add(new BookRecommendation(
                    b.getBookId(), b.getIsbn(), b.getTitle(), b.getAuthor(),
                    b.getPublisher(), b.getPublicationYear(), b.getCategory(),
                    b.getDepartment(), b.getDescription(), b.getCoverImageUrl(),
                    avail, "Similar to your selection", "SIMILAR", 100.0
            ));
        }

        return recommendations;
    }

    @Override
    public String getStrategyName() {
        return "SIMILAR";
    }
}

