package com.psgitech.library.service;

import com.psgitech.library.model.BookRecommendation;
import com.psgitech.library.model.RecommendationContext;

import java.util.List;

/**
 * Strategy interface for book recommendations, trending items, and similarity ranking.
 * Follows the Strategy design pattern (Decision D15).
 */
public interface RecommendationStrategy {
    /**
     * Computes and returns recommended books for the given context.
     *
     * @param context the user, target book, and limit parameters
     * @return ordered list of book recommendations with live availability and labels
     */
    List<BookRecommendation> recommend(RecommendationContext context);

    /**
     * Unique identifier for the strategy.
     */
    String getStrategyName();
}
