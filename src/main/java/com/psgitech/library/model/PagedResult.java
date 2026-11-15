package com.psgitech.library.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Generic container for paginated result sets with total counts and applied filter metadata.
 */
public class PagedResult<T> {
    private final List<T> items;
    private final int page;
    private final int pageSize;
    private final int totalCount;
    private final int totalPages;
    private final Map<String, Object> appliedFilters;
    private final String sort;
    private final boolean extraTermsIgnored;

    public PagedResult(List<T> items, int page, int pageSize, int totalCount) {
        this(items, page, pageSize, totalCount, Collections.emptyMap(), null, false);
    }

    public PagedResult(List<T> items, int page, int pageSize, int totalCount,
                       Map<String, Object> appliedFilters, String sort, boolean extraTermsIgnored) {
        this.items = items != null ? Collections.unmodifiableList(items) : Collections.emptyList();
        this.page = page;
        this.pageSize = pageSize;
        this.totalCount = totalCount;
        this.totalPages = (int) Math.ceil((double) totalCount / Math.max(1, pageSize));
        this.appliedFilters = appliedFilters != null ? Collections.unmodifiableMap(appliedFilters) : Collections.emptyMap();
        this.sort = sort;
        this.extraTermsIgnored = extraTermsIgnored;
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public Map<String, Object> getAppliedFilters() {
        return appliedFilters;
    }

    public String getSort() {
        return sort;
    }

    public boolean isExtraTermsIgnored() {
        return extraTermsIgnored;
    }
}

