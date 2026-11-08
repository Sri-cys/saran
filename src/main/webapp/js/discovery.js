/**
 * PSG iTech Library - Smart Book Discovery & Recommendations JavaScript Module
 * Modules 10.1 - 10.7
 * Accessible combobox, debounced suggestions with AbortController, URL state sync,
 * independent parallel section rendering with skeletons and safe textContent rendering.
 */

document.addEventListener('DOMContentLoaded', () => {
    initDiscoveryApp();
});

function initDiscoveryApp() {
    initNavigation();
    initAutocomplete();
    initFiltersAndSearch();
    loadPopularSection('popular');
    loadCategoriesSection();
    loadPersonalizedSectionIfStudent();
    executeSearchFromUrlParams();
}

// Global state
let currentSearchAbortController = null;
let currentSuggestionsAbortController = null;
let autocompleteDebounceTimer = null;
let selectedSuggestionIndex = -1;

function initNavigation() {
    // Check authentication status for personalized section
    const user = getLoggedInUser();
    const navLoggedOut = document.getElementById('navLoggedOut');
    const navLoggedIn = document.getElementById('navLoggedIn');
    const navUsername = document.getElementById('navUsername');
    const navAvatar = document.getElementById('navAvatar');

    if (user && navLoggedIn && navLoggedOut) {
        navLoggedOut.style.display = 'none';
        navLoggedIn.style.display = 'flex';
        if (navUsername) navUsername.textContent = user.name || 'User';
        if (navAvatar) navAvatar.textContent = (user.name || 'U').charAt(0).toUpperCase();

        const profileTrigger = document.getElementById('profileTrigger');
        const dropdownMenu = document.getElementById('profileDropdownMenu');
        if (profileTrigger && dropdownMenu) {
            profileTrigger.addEventListener('click', (e) => {
                e.stopPropagation();
                dropdownMenu.classList.toggle('active');
            });
            document.addEventListener('click', () => {
                dropdownMenu.classList.remove('active');
            });
        }
    }
}

function getLoggedInUser() {
    try {
        const u = sessionStorage.getItem('user');
        return u ? JSON.parse(u) : null;
    } catch (e) {
        return null;
    }
}

/* =============================================================================
   Module 10.2: Accessible Autocomplete Combobox
   ============================================================================= */
function initAutocomplete() {
    const input = document.getElementById('discoverySearchInput');
    const listbox = document.getElementById('autocompleteListbox');
    const form = document.getElementById('discoverySearchForm');

    if (!input || !listbox) return;

    input.addEventListener('input', (e) => {
        const query = input.value.trim();
        clearTimeout(autocompleteDebounceTimer);
        selectedSuggestionIndex = -1;

        if (query.length < 2) {
            closeAutocomplete();
            return;
        }

        autocompleteDebounceTimer = setTimeout(() => {
            fetchSuggestions(query);
        }, 250);
    });

    input.addEventListener('keydown', (e) => {
        const items = listbox.querySelectorAll('.autocomplete-option');
        if (!items || items.length === 0) return;

        if (e.key === 'ArrowDown') {
            e.preventDefault();
            selectedSuggestionIndex = (selectedSuggestionIndex + 1) % items.length;
            updateHighlightedSuggestion(items);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            selectedSuggestionIndex = (selectedSuggestionIndex - 1 + items.length) % items.length;
            updateHighlightedSuggestion(items);
        } else if (e.key === 'Enter') {
            if (selectedSuggestionIndex >= 0 && selectedSuggestionIndex < items.length) {
                e.preventDefault();
                items[selectedSuggestionIndex].click();
            }
        } else if (e.key === 'Escape' || e.key === 'Tab') {
            closeAutocomplete();
        }
    });

    document.addEventListener('click', (e) => {
        if (!input.contains(e.target) && !listbox.contains(e.target)) {
            closeAutocomplete();
        }
    });

    if (form) {
        form.addEventListener('submit', (e) => {
            e.preventDefault();
            closeAutocomplete();
            applyFilterChange({ q: input.value.trim(), page: 1 });
        });
    }
}

function fetchSuggestions(query) {
    if (currentSuggestionsAbortController) {
        currentSuggestionsAbortController.abort();
    }
    currentSuggestionsAbortController = new AbortController();

    fetch(`books/suggestions?q=${encodeURIComponent(query)}`, {
        signal: currentSuggestionsAbortController.signal,
        headers: { 'Accept': 'application/json' }
    })
    .then(res => {
        if (res.status === 429) {
            throw new Error('Too many requests. Please wait a few seconds.');
        }
        return res.json();
    })
    .then(data => {
        if (data.success && data.suggestions && data.suggestions.length > 0) {
            renderAutocompleteOptions(data.suggestions);
        } else {
            closeAutocomplete();
        }
    })
    .catch(err => {
        if (err.name !== 'AbortError') {
            closeAutocomplete();
        }
    });
}

function renderAutocompleteOptions(suggestions) {
    const listbox = document.getElementById('autocompleteListbox');
    const input = document.getElementById('discoverySearchInput');
    if (!listbox || !input) return;

    listbox.innerHTML = '';
    selectedSuggestionIndex = -1;

    suggestions.forEach((s, idx) => {
        const item = document.createElement('div');
        item.className = 'autocomplete-option';
        item.setAttribute('role', 'option');
        item.setAttribute('id', `suggestion-opt-${idx}`);

        const typeBadge = document.createElement('span');
        typeBadge.className = `suggestion-badge badge-${(s.type || 'TITLE').toLowerCase()}`;
        typeBadge.textContent = s.type || 'TITLE';

        const textSpan = document.createElement('span');
        textSpan.className = 'suggestion-text';
        textSpan.textContent = s.text;

        item.appendChild(typeBadge);
        item.appendChild(textSpan);

        item.addEventListener('click', () => {
            if (s.bookId) {
                window.location.href = `book-details.html?id=${encodeURIComponent(s.bookId)}`;
            } else if (s.type === 'CATEGORY') {
                input.value = '';
                closeAutocomplete();
                applyFilterChange({ category: s.text, q: '', page: 1 });
            } else if (s.type === 'AUTHOR') {
                input.value = '';
                closeAutocomplete();
                applyFilterChange({ author: s.text, q: '', page: 1 });
            } else {
                input.value = s.text;
                closeAutocomplete();
                applyFilterChange({ q: s.text, page: 1 });
            }
        });

        listbox.appendChild(item);
    });

    listbox.style.display = 'block';
    input.setAttribute('aria-expanded', 'true');
}

function updateHighlightedSuggestion(items) {
    const input = document.getElementById('discoverySearchInput');
    items.forEach((item, idx) => {
        if (idx === selectedSuggestionIndex) {
            item.classList.add('selected');
            item.setAttribute('aria-selected', 'true');
            if (input) input.setAttribute('aria-activedescendant', item.id);
        } else {
            item.classList.remove('selected');
            item.setAttribute('aria-selected', 'false');
        }
    });
}

function closeAutocomplete() {
    const listbox = document.getElementById('autocompleteListbox');
    const input = document.getElementById('discoverySearchInput');
    if (listbox) {
        listbox.style.display = 'none';
        listbox.innerHTML = '';
    }
    if (input) {
        input.setAttribute('aria-expanded', 'false');
        input.removeAttribute('aria-activedescendant');
    }
    selectedSuggestionIndex = -1;
}

/* =============================================================================
   Module 10.4: Popular and Trending Section
   ============================================================================= */
function loadPopularSection(type) {
    const container = document.getElementById('popularScrollContainer');
    const popularBtn = document.getElementById('tabBtnPopular');
    const trendingBtn = document.getElementById('tabBtnTrending');

    if (popularBtn && trendingBtn) {
        if (type === 'trending') {
            trendingBtn.classList.add('active');
            popularBtn.classList.remove('active');
        } else {
            popularBtn.classList.add('active');
            trendingBtn.classList.remove('active');
        }
    }

    if (!container) return;
    renderSkeletons(container, 4);

    fetch(`books/popular?type=${encodeURIComponent(type)}&limit=10`)
        .then(res => res.json())
        .then(data => {
            if (data.success && data.books && data.books.length > 0) {
                renderScrollCards(container, data.books);
            } else {
                renderEmptyState(container, 'No trending books recorded yet.');
            }
        })
        .catch(err => {
            renderErrorState(container, 'Unable to load popular books.', () => loadPopularSection(type));
        });
}

/* =============================================================================
   Module 10.5: Personalized Recommendations Section (Student Only)
   ============================================================================= */
function loadPersonalizedSectionIfStudent() {
    const section = document.getElementById('personalizedSection');
    const container = document.getElementById('personalizedScrollContainer');
    const titleEl = document.getElementById('personalizedTitle');
    if (!section || !container) return;

    const user = getLoggedInUser();
    if (!user || user.role !== 'STUDENT') {
        section.style.display = 'none';
        return;
    }

    section.style.display = 'block';
    renderSkeletons(container, 4);

    fetch('books/recommendations?limit=10')
        .then(res => {
            if (res.status === 401 || res.status === 403) {
                section.style.display = 'none';
                return null;
            }
            return res.json();
        })
        .then(data => {
            if (!data) return;
            if (data.success && data.recommendations && data.recommendations.length > 0) {
                const strat = data.recommendations[0].strategy;
                if (titleEl) {
                    if (strat === 'PERSONALIZED') {
                        titleEl.textContent = 'Recommended For You';
                    } else if (strat === 'DEPARTMENT') {
                        titleEl.textContent = 'Popular in Your Department';
                    } else {
                        titleEl.textContent = 'Popular Books You May Like';
                    }
                }
                renderScrollCards(container, data.recommendations);
            } else {
                renderEmptyState(container, 'No personalized recommendations yet. Browse the catalog to get started!');
            }
        })
        .catch(err => {
            renderErrorState(container, 'Unable to load recommendations.', () => loadPersonalizedSectionIfStudent());
        });
}

/* =============================================================================
   Module 10.7: Discover by Category Section
   ============================================================================= */
function loadCategoriesSection() {
    const container = document.getElementById('categoriesGridContainer');
    if (!container) return;

    fetch('books/categories')
        .then(res => res.json())
        .then(data => {
            if (data.success && data.categories && data.categories.length > 0) {
                renderCategoryCards(container, data.categories);
            } else {
                renderEmptyState(container, 'No categories found.');
            }
        })
        .catch(err => {
            renderErrorState(container, 'Unable to load categories.', () => loadCategoriesSection());
        });
}

function renderCategoryCards(container, categories) {
    container.innerHTML = '';
    categories.forEach(cat => {
        const card = document.createElement('div');
        card.className = 'category-pill-card';
        card.setAttribute('role', 'button');
        card.setAttribute('tabindex', '0');

        const title = document.createElement('h4');
        title.className = 'category-pill-title';
        title.textContent = cat.displayName;

        const meta = document.createElement('div');
        meta.className = 'category-pill-meta';
        meta.textContent = `${cat.bookCount} titles • ${cat.availableCopyCount} available`;

        card.appendChild(title);
        card.appendChild(meta);

        const selectCat = () => {
            applyFilterChange({ category: cat.displayName, page: 1 });
            const resultsSection = document.getElementById('resultsSection');
            if (resultsSection) {
                resultsSection.scrollIntoView({ behavior: 'smooth' });
            }
        };

        card.addEventListener('click', selectCat);
        card.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                selectCat();
            }
        });

        container.appendChild(card);
    });
}

/* =============================================================================
   Modules 10.1 & 10.3: Search, Filters and Paginated Results
   ============================================================================= */
function initFiltersAndSearch() {
    const filterForm = document.getElementById('advancedFilterForm');
    const resetBtn = document.getElementById('resetFiltersBtn');
    const sortSelect = document.getElementById('sortBySelect');
    const pageSizeSelect = document.getElementById('pageSizeSelect');

    if (filterForm) {
        filterForm.addEventListener('submit', (e) => {
            e.preventDefault();
            const formData = new FormData(filterForm);
            const params = {
                category: formData.get('category') || '',
                author: formData.get('author') || '',
                publisher: formData.get('publisher') || '',
                department: formData.get('department') || '',
                yearFrom: formData.get('yearFrom') || '',
                yearTo: formData.get('yearTo') || '',
                availability: formData.get('availability') || 'ALL',
                page: 1
            };
            applyFilterChange(params);
        });
    }

    if (resetBtn) {
        resetBtn.addEventListener('click', () => {
            if (filterForm) filterForm.reset();
            const input = document.getElementById('discoverySearchInput');
            if (input) input.value = '';
            applyFilterChange({
                q: '', category: '', author: '', publisher: '', department: '',
                yearFrom: '', yearTo: '', availability: 'ALL', sort: 'RECENTLY_ADDED', page: 1
            });
        });
    }

    if (sortSelect) {
        sortSelect.addEventListener('change', () => {
            applyFilterChange({ sort: sortSelect.value, page: 1 });
        });
    }

    if (pageSizeSelect) {
        pageSizeSelect.addEventListener('change', () => {
            applyFilterChange({ pageSize: pageSizeSelect.value, page: 1 });
        });
    }

    // Tab buttons for popular/trending
    const popBtn = document.getElementById('tabBtnPopular');
    const trendBtn = document.getElementById('tabBtnTrending');
    if (popBtn) popBtn.addEventListener('click', () => loadPopularSection('popular'));
    if (trendBtn) trendBtn.addEventListener('click', () => loadPopularSection('trending'));
}

function applyFilterChange(newParams) {
    const currentParams = getUrlParams();
    const merged = { ...currentParams, ...newParams };

    // Clean empty values
    Object.keys(merged).forEach(k => {
        if (merged[k] === '' || merged[k] === null || merged[k] === undefined) {
            delete merged[k];
        }
    });

    const queryString = new URLSearchParams(merged).toString();
    const newUrl = window.location.pathname + (queryString ? '?' + queryString : '');
    window.history.replaceState({}, '', newUrl);

    syncUiInputsWithParams(merged);
    executeSearch(merged);
}

function getUrlParams() {
    const searchParams = new URLSearchParams(window.location.search);
    const params = {};
    for (const [k, v] of searchParams.entries()) {
        params[k] = v;
    }
    return params;
}

function executeSearchFromUrlParams() {
    const params = getUrlParams();
    syncUiInputsWithParams(params);
    executeSearch(params);
}

function syncUiInputsWithParams(params) {
    const searchInput = document.getElementById('discoverySearchInput');
    if (searchInput && params.q !== undefined) searchInput.value = params.q;

    const filterForm = document.getElementById('advancedFilterForm');
    if (filterForm) {
        const setVal = (name, val) => {
            const el = filterForm.elements[name];
            if (el) el.value = val || '';
        };
        setVal('category', params.category);
        setVal('author', params.author);
        setVal('publisher', params.publisher);
        setVal('department', params.department);
        setVal('yearFrom', params.yearFrom);
        setVal('yearTo', params.yearTo);
        setVal('availability', params.availability || 'ALL');
    }

    const sortSelect = document.getElementById('sortBySelect');
    if (sortSelect && params.sort) sortSelect.value = params.sort;

    const pageSizeSelect = document.getElementById('pageSizeSelect');
    if (pageSizeSelect && params.pageSize) pageSizeSelect.value = params.pageSize;

    renderActiveFilterChips(params);
}

function renderActiveFilterChips(params) {
    const container = document.getElementById('activeFilterChipsContainer');
    if (!container) return;

    container.innerHTML = '';
    const chipKeys = ['q', 'category', 'author', 'publisher', 'department', 'yearFrom', 'yearTo', 'availability'];

    chipKeys.forEach(key => {
        const val = params[key];
        if (val && !(key === 'availability' && val === 'ALL')) {
            const chip = document.createElement('span');
            chip.className = 'filter-chip';

            const label = document.createElement('span');
            label.textContent = `${key}: ${val}`;

            const removeBtn = document.createElement('button');
            removeBtn.type = 'button';
            removeBtn.setAttribute('aria-label', `Remove ${key} filter`);
            removeBtn.innerHTML = '&times;';
            removeBtn.addEventListener('click', () => {
                const updated = {};
                updated[key] = '';
                applyFilterChange(updated);
            });

            chip.appendChild(label);
            chip.appendChild(removeBtn);
            container.appendChild(chip);
        }
    });
}

function executeSearch(params) {
    const container = document.getElementById('resultsGridContainer');
    const countEl = document.getElementById('resultsCountText');
    const paginationContainer = document.getElementById('paginationControlsContainer');

    if (!container) return;
    renderSkeletons(container, 6);

    if (currentSearchAbortController) {
        currentSearchAbortController.abort();
    }
    currentSearchAbortController = new AbortController();

    const qs = new URLSearchParams(params).toString();

    fetch(`books/discover?${qs}`, {
        signal: currentSearchAbortController.signal,
        headers: { 'Accept': 'application/json' }
    })
    .then(res => res.json())
    .then(data => {
        if (data.success && data.data) {
            const paged = data.data;
            if (countEl) {
                countEl.textContent = `Showing ${paged.items.length} of ${paged.totalCount} books`;
                if (paged.extraTermsIgnored) {
                    countEl.textContent += ' (maximum 5 terms applied)';
                }
            }
            if (paged.items && paged.items.length > 0) {
                renderResultCards(container, paged.items);
                renderPagination(paginationContainer, paged);
            } else {
                renderEmptyState(container, 'No books matched your criteria. Try adjusting your search or filters.');
                if (paginationContainer) paginationContainer.innerHTML = '';
            }
        } else {
            renderErrorState(container, data.message || 'Error loading search results.', () => executeSearch(params));
        }
    })
    .catch(err => {
        if (err.name !== 'AbortError') {
            renderErrorState(container, 'Network error loading search results.', () => executeSearch(params));
        }
    });
}

/* =============================================================================
   Card and Element Renderers (Strict textContent only, NO unsafe innerHTML)
   ============================================================================= */
function renderResultCards(container, books) {
    container.innerHTML = '';
    books.forEach(b => {
        container.appendChild(createBookCard(b));
    });
}

function renderScrollCards(container, books) {
    container.innerHTML = '';
    books.forEach(b => {
        container.appendChild(createBookCard(b));
    });
}

function createBookCard(book) {
    const card = document.createElement('div');
    card.className = 'book-card discovery-card';

    // Cover
    const coverContainer = document.createElement('div');
    coverContainer.className = 'book-cover-container';

    if (book.coverImageUrl && isValidHttpUrl(book.coverImageUrl)) {
        const img = document.createElement('img');
        img.src = book.coverImageUrl;
        img.alt = book.title;
        img.className = 'book-cover-img';
        img.loading = 'lazy';
        img.referrerPolicy = 'no-referrer';
        img.onerror = () => {
            img.style.display = 'none';
            coverContainer.appendChild(createMockCover(book));
        };
        coverContainer.appendChild(img);
    } else {
        coverContainer.appendChild(createMockCover(book));
    }
    card.appendChild(coverContainer);

    // Book Info
    const info = document.createElement('div');
    info.className = 'book-info';

    // Optional Reason / Label Badge
    if (book.label) {
        const labelBadge = document.createElement('div');
        labelBadge.className = 'book-label-badge';
        labelBadge.textContent = book.label;
        info.appendChild(labelBadge);
    }

    // Title
    const title = document.createElement('h3');
    title.className = 'book-title';
    title.textContent = book.title;
    info.appendChild(title);

    // Author
    const author = document.createElement('div');
    author.className = 'author-name';
    author.textContent = book.author || 'Unknown Author';
    info.appendChild(author);

    // Meta details (Category & Year)
    const meta = document.createElement('div');
    meta.className = 'book-meta-line text-secondary';
    meta.textContent = `${book.category || 'General'} • ${book.publicationYear || ''}`;
    info.appendChild(meta);

    // Live Availability Badge
    const avail = book.availability || (book.inventory ? {
        availableCopies: book.inventory.availableCopies,
        totalActiveCopies: book.inventory.totalActiveCopies,
        status: book.inventory.availableCopies > 0 ? 'AVAILABLE' : 'UNAVAILABLE'
    } : null);

    const badge = document.createElement('div');
    badge.className = 'book-availability-block';

    if (avail && avail.availableCopies > 0) {
        const span = document.createElement('span');
        span.className = 'badge badge-available';
        span.textContent = `Available (${avail.availableCopies} copies)`;
        badge.appendChild(span);
    } else if (avail && avail.totalActiveCopies > 0) {
        const span = document.createElement('span');
        span.className = 'badge badge-borrowed';
        span.textContent = 'All copies borrowed / on hold';
        badge.appendChild(span);
    } else {
        const span = document.createElement('span');
        span.className = 'badge badge-archived';
        span.textContent = 'No physical copies';
        badge.appendChild(span);
    }
    info.appendChild(badge);

    // View Details link
    const linkContainer = document.createElement('div');
    linkContainer.className = 'card-action-row';
    const link = document.createElement('a');
    link.href = `book-details.html?id=${encodeURIComponent(book.bookId)}`;
    link.className = 'btn btn-secondary btn-sm';
    link.textContent = 'View Details';
    linkContainer.appendChild(link);
    info.appendChild(linkContainer);

    card.appendChild(info);
    return card;
}

function createMockCover(book) {
    const mock = document.createElement('div');
    mock.className = 'book-cover-mock mock-cover-cs';

    const dept = document.createElement('span');
    dept.className = 'mock-dept';
    dept.textContent = book.department || book.category || 'PSG iTech';

    const title = document.createElement('span');
    title.className = 'mock-title';
    title.textContent = book.title;

    const author = document.createElement('span');
    author.className = 'mock-author';
    author.textContent = book.author;

    mock.appendChild(dept);
    mock.appendChild(title);
    mock.appendChild(author);
    return mock;
}

function isValidHttpUrl(string) {
    try {
        const url = new URL(string);
        return url.protocol === 'http:' || url.protocol === 'https:';
    } catch (_) {
        return false;
    }
}

/* =============================================================================
   State Visuals: Skeletons, Empty State, Error State, Pagination
   ============================================================================= */
function renderSkeletons(container, count) {
    container.innerHTML = '';
    for (let i = 0; i < count; i++) {
        const skel = document.createElement('div');
        skel.className = 'skeleton-card';
        container.appendChild(skel);
    }
}

function renderEmptyState(container, message) {
    container.innerHTML = '';
    const empty = document.createElement('div');
    empty.className = 'discovery-empty-state';
    const p = document.createElement('p');
    p.className = 'text-secondary';
    p.textContent = message;
    empty.appendChild(p);
    container.appendChild(empty);
}

function renderErrorState(container, message, retryFn) {
    container.innerHTML = '';
    const errDiv = document.createElement('div');
    errDiv.className = 'discovery-error-state alert alert-error';

    const p = document.createElement('p');
    p.textContent = message;
    errDiv.appendChild(p);

    if (retryFn) {
        const retryBtn = document.createElement('button');
        retryBtn.type = 'button';
        retryBtn.className = 'btn btn-secondary btn-sm';
        retryBtn.style.marginTop = '8px';
        retryBtn.textContent = 'Retry';
        retryBtn.addEventListener('click', retryFn);
        errDiv.appendChild(retryBtn);
    }

    container.appendChild(errDiv);
}

function renderPagination(container, paged) {
    if (!container) return;
    container.innerHTML = '';

    if (paged.totalPages <= 1) return;

    const nav = document.createElement('div');
    nav.className = 'pagination-controls';

    // Prev Button
    const prevBtn = document.createElement('button');
    prevBtn.className = 'btn btn-secondary btn-sm';
    prevBtn.textContent = '← Previous';
    prevBtn.disabled = (paged.page <= 1);
    prevBtn.addEventListener('click', () => {
        if (paged.page > 1) applyFilterChange({ page: paged.page - 1 });
    });
    nav.appendChild(prevBtn);

    // Page Info
    const pageInfo = document.createElement('span');
    pageInfo.className = 'pagination-info text-secondary';
    pageInfo.textContent = `Page ${paged.page} of ${paged.totalPages}`;
    nav.appendChild(pageInfo);

    // Next Button
    const nextBtn = document.createElement('button');
    nextBtn.className = 'btn btn-secondary btn-sm';
    nextBtn.textContent = 'Next →';
    nextBtn.disabled = (paged.page >= paged.totalPages);
    nextBtn.addEventListener('click', () => {
        if (paged.page < paged.totalPages) applyFilterChange({ page: paged.page + 1 });
    });
    nav.appendChild(nextBtn);

    container.appendChild(nav);
}

