package com.psgitech.library.service;

import com.psgitech.library.dto.librarian.DashboardStatsDTO;
import com.psgitech.library.repository.LibrarianDashboardRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service orchestrating dashboard metrics, home collections, analytics, and global search.
 */
public class LibrarianDashboardService {
    private final LibrarianDashboardRepository repository;

    public LibrarianDashboardService() {
        this.repository = new LibrarianDashboardRepository();
    }

    public LibrarianDashboardService(LibrarianDashboardRepository repository) {
        this.repository = repository;
    }

    public DashboardStatsDTO getDashboardStats() {
        DashboardStatsDTO stats = repository.getStats();
        // Load default 30d department statistics for home view
        stats.setBookDepartmentStats(repository.getBorrowingByBookDepartment(30));
        stats.setStudentDepartmentStats(repository.getBorrowingByStudentDepartment(30));
        return stats;
    }

    public Map<String, Object> getChartsData(int daysWindow) {
        Map<String, Object> charts = new HashMap<>();
        charts.put("bookDepartmentStats", repository.getBorrowingByBookDepartment(daysWindow));
        charts.put("studentDepartmentStats", repository.getBorrowingByStudentDepartment(daysWindow));
        charts.put("daysWindow", daysWindow);
        return charts;
    }

    public Map<String, Object> getHomeCollections() {
        Map<String, Object> data = new HashMap<>();
        int threshold = repository.getHighDemandThreshold();
        data.put("recentlyAdded", repository.getRecentlyAddedBooks(10));
        data.put("mostBorrowed", repository.getMostBorrowedBooks(10));
        data.put("highDemand", repository.getHighDemandBooks(threshold, 10));
        data.put("categories", repository.getCategoryCounts());
        data.put("highDemandThreshold", threshold);
        return data;
    }

    public Map<String, List<Map<String, Object>>> performGlobalSearch(String query) {
        if (query == null || query.trim().length() < 2) {
            Map<String, List<Map<String, Object>>> empty = new HashMap<>();
            empty.put("books", List.of());
            empty.put("students", List.of());
            empty.put("transactions", List.of());
            return empty;
        }
        return repository.globalSearch(query.trim());
    }
}
