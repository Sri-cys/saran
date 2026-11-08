package com.psgitech.library.model;

/**
 * Context parameter passed to recommendation strategies.
 */
public class RecommendationContext {
    private final Integer studentId;
    private final String department;
    private final Integer targetBookId;
    private final int limit;
    private final String recommendationType; // "popular", "trending", "personalized", "similar"

    public RecommendationContext(Integer studentId, String department, Integer targetBookId,
                                 int limit, String recommendationType) {
        this.studentId = studentId;
        this.department = department;
        this.targetBookId = targetBookId;
        this.limit = limit > 0 ? limit : 10;
        this.recommendationType = recommendationType;
    }

    public static RecommendationContext forStudent(int studentId, String department, int limit) {
        return new RecommendationContext(studentId, department, null, limit, "personalized");
    }

    public static RecommendationContext forPopular(int limit, String type) {
        return new RecommendationContext(null, null, null, limit, type);
    }

    public static RecommendationContext forSimilar(int bookId, int limit) {
        return new RecommendationContext(null, null, bookId, limit, "similar");
    }

    public Integer getStudentId() {
        return studentId;
    }

    public String getDepartment() {
        return department;
    }

    public Integer getTargetBookId() {
        return targetBookId;
    }

    public int getLimit() {
        return limit;
    }

    public String getRecommendationType() {
        return recommendationType;
    }
}

