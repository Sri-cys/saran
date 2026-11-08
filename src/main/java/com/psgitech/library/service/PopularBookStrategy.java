package com.psgitech.library.service;

import com.psgitech.library.model.BookRecommendation;
import com.psgitech.library.model.RecommendationContext;

import java.util.List;

/**
 * Recommendation strategy serving global popular or trending books (Decision D4/D15).
 */
public class PopularBookStrategy implements RecommendationStrategy {
    private final BookPopularityService popularityService;

    public PopularBookStrategy() {
        this.popularityService = new BookPopularityService();
    }

    public PopularBookStrategy(BookPopularityService popularityService) {
        this.popularityService = popularityService;
    }

    @Override
    public List<BookRecommendation> recommend(RecommendationContext context) {
        if ("trending".equalsIgnoreCase(context.getRecommendationType())) {
            return popularityService.getTrendingBooks(context.getLimit());
        }
        return popularityService.getPopularBooks(context.getLimit());
    }

    @Override
    public String getStrategyName() {
        return "POPULAR_FALLBACK";
    }
}

