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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * High-level orchestration service for book recommendations.
 * Selects and executes appropriate RecommendationStrategy instances, applies student department signals,
 * and manages the cold-start fallback chain (Decisions D6 and D15).
 */
public class BookRecommendationService {
    private final RecommendationRepository recommendationRepository;
    private final DiscoveryRepository discoveryRepository;
    private final BookPopularityService popularityService;

    private final CategoryBasedStrategy personalizedStrategy;
    private final PopularBookStrategy popularStrategy;
    private final SimilarBookStrategy similarStrategy;

    public BookRecommendationService() {
        this.recommendationRepository = new RecommendationRepository();
        this.discoveryRepository = new DiscoveryRepository();
        this.popularityService = new BookPopularityService();

        this.personalizedStrategy = new CategoryBasedStrategy(recommendationRepository, discoveryRepository);
        this.popularStrategy = new PopularBookStrategy(popularityService);
        this.similarStrategy = new SimilarBookStrategy(recommendationRepository, discoveryRepository);
    }

    public BookRecommendationService(RecommendationRepository recommendationRepository,
                                     DiscoveryRepository discoveryRepository,
                                     BookPopularityService popularityService) {
        this.recommendationRepository = recommendationRepository;
        this.discoveryRepository = discoveryRepository;
        this.popularityService = popularityService;

        this.personalizedStrategy = new CategoryBasedStrategy(recommendationRepository, discoveryRepository);
        this.popularStrategy = new PopularBookStrategy(popularityService);
        this.similarStrategy = new SimilarBookStrategy(recommendationRepository, discoveryRepository);
    }

    /**
     * Retrieves personalized recommendations for a student.
     * Implements D6:
     * - Primary: Personalized CategoryBasedStrategy if history >= 2.
     * - Cold-Start Fallback 1: Department-based recommendations ("Popular in your department").
     * - Cold-Start Fallback 2: Global Popular books ("Popular Books You May Like").
     * Cold start never returns an empty panel.
     */
    public List<BookRecommendation> getStudentRecommendations(int studentId, String department, int limit) {
        RecommendationContext context = RecommendationContext.forStudent(studentId, department, limit);

        // 1. Try personalized strategy
        List<BookRecommendation> recommendations = personalizedStrategy.recommend(context);
        if (!recommendations.isEmpty()) {
            return recommendations;
        }

        // 2. Cold-start Fallback: Try student's department if available
        Set<Integer> excluded = recommendationRepository.getExcludedBookIdsForStudent(studentId);
        if (department != null && !department.trim().isEmpty()) {
            List<Book> deptBooks = recommendationRepository.getDepartmentBooks(department, excluded, limit);
            if (deptBooks != null && !deptBooks.isEmpty()) {
                List<Integer> deptIds = deptBooks.stream().map(Book::getBookId).collect(Collectors.toList());
                Map<Integer, AvailabilityInfo> availMap = discoveryRepository.getBatchedAvailability(deptIds);

                List<BookRecommendation> deptRecs = new ArrayList<>();
                for (Book b : deptBooks) {
                    AvailabilityInfo avail = availMap.get(b.getBookId());
                    if (avail == null) {
                        avail = new AvailabilityInfo(0, 0, 0, 0, 0, 0);
                    }
                    deptRecs.add(new BookRecommendation(
                            b.getBookId(), b.getIsbn(), b.getTitle(), b.getAuthor(),
                            b.getPublisher(), b.getPublicationYear(), b.getCategory(),
                            b.getDepartment(), b.getDescription(), b.getCoverImageUrl(),
                            avail, "Popular in your department (" + department + ")", "DEPARTMENT", 75.0
                    ));
                }
                if (deptRecs.size() >= 3) {
                    return deptRecs;
                }
            }
        }

        // 3. Cold-start Fallback 2: Global Popular books
        List<BookRecommendation> globalPopular = popularStrategy.recommend(context);
        List<BookRecommendation> filteredPopular = new ArrayList<>();
        for (BookRecommendation br : globalPopular) {
            if (!excluded.contains(br.getBookId())) {
                filteredPopular.add(new BookRecommendation(
                        br.getBookId(), br.getIsbn(), br.getTitle(), br.getAuthor(),
                        br.getPublisher(), br.getPublicationYear(), br.getCategory(),
                        br.getDepartment(), br.getDescription(), br.getCoverImageUrl(),
                        br.getAvailability(), "Popular Books You May Like", "POPULAR_FALLBACK", br.getScore()
                ));
            }
        }

        return !filteredPopular.isEmpty() ? filteredPopular : globalPopular;
    }

    /**
     * Retrieves similar books for a target book ID (Module 10.6).
     */
    public List<BookRecommendation> getSimilarBooks(int bookId, int limit) {
        RecommendationContext context = RecommendationContext.forSimilar(bookId, limit);
        return similarStrategy.recommend(context);
    }
}
