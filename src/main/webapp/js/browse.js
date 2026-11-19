/**
 * PSG iTech Library - Professional Academic Catalog (browse.js)
 * Implements:
 * - "Explore Academic Categories" interactive horizontal card strip
 * - Two-column responsive workspace (sticky filters + single-row toolbar + responsive book grid)
 * - Removable active filter pills, URL param syncing, server-side pagination, XSS safety.
 */

(function () {
    'use strict';

    // State management
    const state = {
        keyword: '',
        category: 'ALL',
        author: '',
        publisher: '',
        department: 'ALL',
        yearFrom: null,
        yearTo: null,
        availability: 'ALL',
        sort: 'RECENTLY_ADDED',
        page: 1,
        pageSize: 12,
        categories: []
    };

    document.addEventListener('DOMContentLoaded', function () {
        initCatalogPage();
    });

    function initCatalogPage() {
        parseInitialUrlParams();
        loadCategories();
        bindFilterEvents();
        fetchAndRenderBooks();
    }

    /**
     * Reads initial parameters from query string (e.g. from header search or home page)
     */
    function parseInitialUrlParams() {
        const params = new URLSearchParams(window.location.search);
        if (params.has('q')) {
            state.keyword = params.get('q').trim();
            const topSearchInput = document.getElementById('navSearchInput');
            if (topSearchInput) topSearchInput.value = state.keyword;
        }
        if (params.has('category')) {
            state.category = params.get('category').trim();
        }
        if (params.has('department')) {
            state.department = params.get('department').trim();
            const deptSelect = document.getElementById('filterDeptSelect');
            if (deptSelect) deptSelect.value = state.department;
        }
        if (params.has('author')) {
            state.author = params.get('author').trim();
            const authorInput = document.getElementById('filterAuthorInput');
            if (authorInput) authorInput.value = state.author;
        }
        if (params.has('publisher')) {
            state.publisher = params.get('publisher').trim();
            const pubInput = document.getElementById('filterPublisherInput');
            if (pubInput) pubInput.value = state.publisher;
        }
        if (params.has('sort')) {
            state.sort = params.get('sort').trim();
            const sortSelect = document.getElementById('catalogSortSelect');
            if (sortSelect) sortSelect.value = state.sort;
        }
    }

    /**
     * Loads live categories and populates the horizontal card strip and category dropdown
     */
    function loadCategories() {
        const stripContainer = document.getElementById('categoryStripScroll');
        const catSelect = document.getElementById('filterCategorySelect');

        fetch('books/categories', {
            headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
        })
        .then(res => res.json())
        .then(data => {
            if (data.success && Array.isArray(data.categories) && data.categories.length > 0) {
                state.categories = data.categories;
                renderCategoryStrip(data.categories);
                populateCategorySelect(data.categories);
            } else {
                renderDefaultCategoryStrip();
            }
        })
        .catch(err => {
            console.warn('Could not fetch categories from server, loading defaults:', err);
            renderDefaultCategoryStrip();
        });
    }

    function renderDefaultCategoryStrip() {
        const defaults = [
            { displayName: 'Computer Science', bookCount: 18, availableCopyCount: 32 },
            { displayName: 'Programming', bookCount: 15, availableCopyCount: 24 },
            { displayName: 'Data Structures', bookCount: 12, availableCopyCount: 18 },
            { displayName: 'Artificial Intelligence', bookCount: 10, availableCopyCount: 15 },
            { displayName: 'Mathematics', bookCount: 14, availableCopyCount: 22 },
            { displayName: 'Electronics', bookCount: 11, availableCopyCount: 19 },
            { displayName: 'General Knowledge', bookCount: 8, availableCopyCount: 14 },
            { displayName: 'Fiction', bookCount: 9, availableCopyCount: 12 }
        ];
        state.categories = defaults;
        renderCategoryStrip(defaults);
        populateCategorySelect(defaults);
    }

    function renderCategoryStrip(categories) {
        const strip = document.getElementById('categoryStripScroll');
        if (!strip) return;

        strip.innerHTML = categories.map(cat => {
            const isSelected = state.category && (state.category.toLowerCase() === cat.displayName.toLowerCase());
            const titleEsc = escapeHtml(cat.displayName);
            const titlesCount = cat.bookCount || 0;
            const availCount = cat.availableCopyCount || 0;

            return `
                <div class="category-card-item ${isSelected ? 'active' : ''}"
                     role="button"
                     tabindex="0"
                     aria-pressed="${isSelected ? 'true' : 'false'}"
                     data-category="${titleEsc}">
                    <div class="card-cat-title" title="${titleEsc}">${titleEsc}</div>
                    <div class="card-cat-subtitle">${titlesCount} titles &bull; ${availCount} available</div>
                </div>
            `;
        }).join('');

        // Attach click & keyboard listeners to category cards
        strip.querySelectorAll('.category-card-item').forEach(card => {
            const catName = card.getAttribute('data-category');
            const handler = function () {
                toggleCategoryCard(catName);
            };
            card.addEventListener('click', handler);
            card.addEventListener('keydown', function (e) {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    handler();
                }
            });
        });
    }

    function toggleCategoryCard(categoryName) {
        if (state.category && state.category.toLowerCase() === categoryName.toLowerCase()) {
            // Already active -> clear filter
            state.category = 'ALL';
        } else {
            // Activate filter
            state.category = categoryName;
        }

        const catSelect = document.getElementById('filterCategorySelect');
        if (catSelect) {
            catSelect.value = state.category;
        }

        updateCategoryCardVisuals();
        state.page = 1;
        fetchAndRenderBooks();
    }

    function updateCategoryCardVisuals() {
        const strip = document.getElementById('categoryStripScroll');
        if (!strip) return;
        strip.querySelectorAll('.category-card-item').forEach(card => {
            const cat = card.getAttribute('data-category');
            const isActive = state.category && state.category.toLowerCase() === cat.toLowerCase();
            card.setAttribute('aria-pressed', isActive ? 'true' : 'false');
            if (isActive) {
                card.classList.add('active');
            } else {
                card.classList.remove('active');
            }
        });
    }

    function populateCategorySelect(categories) {
        const select = document.getElementById('filterCategorySelect');
        if (!select) return;

        select.innerHTML = '<option value="ALL">All Categories</option>' +
            categories.map(c => `<option value="${escapeHtml(c.displayName)}">${escapeHtml(c.displayName)}</option>`).join('');

        if (state.category && state.category !== 'ALL') {
            select.value = state.category;
        }
    }

    /**
     * Binds all filter form, toolbar, and reset events
     */
    function bindFilterEvents() {
        const btnApply = document.getElementById('btnApplyFilters');
        const btnReset = document.getElementById('btnResetFilters');
        const filterForm = document.getElementById('catalogFilterForm');
        const sortSelect = document.getElementById('catalogSortSelect');
        const pageSizeSelect = document.getElementById('catalogPageSizeSelect');
        const catSelect = document.getElementById('filterCategorySelect');

        if (catSelect) {
            catSelect.addEventListener('change', function () {
                state.category = catSelect.value;
                updateCategoryCardVisuals();
            });
        }

        if (btnApply) {
            btnApply.addEventListener('click', applyFiltersFromForm);
        }
        if (filterForm) {
            filterForm.addEventListener('submit', function (e) {
                e.preventDefault();
                applyFiltersFromForm();
            });
        }

        if (btnReset) {
            btnReset.addEventListener('click', resetAllFilters);
        }

        if (sortSelect) {
            sortSelect.addEventListener('change', function () {
                state.sort = sortSelect.value;
                state.page = 1;
                fetchAndRenderBooks();
            });
        }

        if (pageSizeSelect) {
            pageSizeSelect.addEventListener('change', function () {
                state.pageSize = parseInt(pageSizeSelect.value, 10) || 12;
                state.page = 1;
                fetchAndRenderBooks();
            });
        }

        // Top navbar search form integration
        const navSearchForm = document.getElementById('navSearchForm');
        const navSearchInput = document.getElementById('navSearchInput');
        if (navSearchForm && navSearchInput) {
            navSearchForm.addEventListener('submit', function (e) {
                e.preventDefault();
                state.keyword = navSearchInput.value.trim();
                state.page = 1;
                fetchAndRenderBooks();
            });
        }
    }

    function applyFiltersFromForm() {
        const catSelect = document.getElementById('filterCategorySelect');
        const authorInput = document.getElementById('filterAuthorInput');
        const pubInput = document.getElementById('filterPublisherInput');
        const deptSelect = document.getElementById('filterDeptSelect');
        const yfInput = document.getElementById('filterYearFrom');
        const ytInput = document.getElementById('filterYearTo');
        const availSelect = document.getElementById('filterAvailabilitySelect');
        const yearError = document.getElementById('yearErrorMsg');

        state.category = catSelect ? catSelect.value : 'ALL';
        state.author = authorInput ? authorInput.value.trim() : '';
        state.publisher = pubInput ? pubInput.value.trim() : '';
        state.department = deptSelect ? deptSelect.value : 'ALL';
        state.availability = availSelect ? availSelect.value : 'ALL';

        let yf = yfInput && yfInput.value.trim() ? parseInt(yfInput.value.trim(), 10) : null;
        let yt = ytInput && ytInput.value.trim() ? parseInt(ytInput.value.trim(), 10) : null;

        if (yf !== null && yt !== null && yf > yt) {
            if (yearError) yearError.style.display = 'block';
            return;
        } else {
            if (yearError) yearError.style.display = 'none';
        }

        state.yearFrom = yf;
        state.yearTo = yt;
        state.page = 1;

        updateCategoryCardVisuals();
        fetchAndRenderBooks();
    }

    function resetAllFilters() {
        state.keyword = '';
        state.category = 'ALL';
        state.author = '';
        state.publisher = '';
        state.department = 'ALL';
        state.yearFrom = null;
        state.yearTo = null;
        state.availability = 'ALL';
        state.page = 1;

        const catSelect = document.getElementById('filterCategorySelect');
        const authorInput = document.getElementById('filterAuthorInput');
        const pubInput = document.getElementById('filterPublisherInput');
        const deptSelect = document.getElementById('filterDeptSelect');
        const yfInput = document.getElementById('filterYearFrom');
        const ytInput = document.getElementById('filterYearTo');
        const availSelect = document.getElementById('filterAvailabilitySelect');
        const yearError = document.getElementById('yearErrorMsg');
        const navSearch = document.getElementById('navSearchInput');

        if (catSelect) catSelect.value = 'ALL';
        if (authorInput) authorInput.value = '';
        if (pubInput) pubInput.value = '';
        if (deptSelect) deptSelect.value = 'ALL';
        if (yfInput) yfInput.value = '';
        if (ytInput) ytInput.value = '';
        if (availSelect) availSelect.value = 'ALL';
        if (yearError) yearError.style.display = 'none';
        if (navSearch) navSearch.value = '';

        updateCategoryCardVisuals();
        fetchAndRenderBooks();
    }

    /**
     * Executes the catalog search request and renders cards & pills
     */
    function fetchAndRenderBooks() {
        const grid = document.getElementById('catalogGrid');
        const countDisplay = document.getElementById('catalogResultCount');
        if (!grid) return;

        grid.innerHTML = `
            <div style="grid-column: 1 / -1; text-align: center; padding: 48px; color: #5F6368;">
                <div style="font-size: 1.1rem; margin-bottom: 8px;">Loading catalog collection...</div>
            </div>
        `;

        renderActiveFilterPills();

        const params = new URLSearchParams();
        if (state.keyword) params.append('q', state.keyword);
        if (state.category && state.category !== 'ALL') params.append('category', state.category);
        if (state.author) params.append('author', state.author);
        if (state.publisher) params.append('publisher', state.publisher);
        if (state.department && state.department !== 'ALL') params.append('department', state.department);
        if (state.yearFrom !== null) params.append('yearFrom', state.yearFrom);
        if (state.yearTo !== null) params.append('yearTo', state.yearTo);
        if (state.availability && state.availability !== 'ALL') params.append('availability', state.availability);
        if (state.sort) params.append('sort', state.sort);
        params.append('page', state.page);
        params.append('pageSize', state.pageSize);

        fetch(`books/discover?${params.toString()}`, {
            headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
        })
        .then(res => res.json())
        .then(data => {
            if (data.success && data.data && Array.isArray(data.data.items)) {
                const books = data.data.items;
                const total = data.data.totalCount || 0;
                const page = data.data.page || 1;
                const totalPages = data.data.totalPages || 1;

                if (countDisplay) {
                    const start = total === 0 ? 0 : (page - 1) * state.pageSize + 1;
                    const end = Math.min(page * state.pageSize, total);
                    countDisplay.textContent = `Showing ${start}–${end} of ${total} books`;
                }

                if (books.length === 0) {
                    grid.innerHTML = `
                        <div style="grid-column: 1 / -1; text-align: center; padding: 60px 20px; background: #FFFFFF; border: 1px solid #E2DDD5; border-radius: 8px;">
                            <h3 style="color: #203B60; font-family: 'Times New Roman', Times, serif; font-size: 1.3rem; margin-bottom: 8px;">No Books Found</h3>
                            <p class="text-secondary" style="max-width: 480px; margin: 0 auto 16px; color: #5F6368; font-size: 0.95rem;">
                                No books match your current filter selections. Try broadening your keywords, category, or year range.
                            </p>
                            <button type="button" class="btn btn-secondary btn-sm" id="btnEmptyReset" style="border: 1px solid #E2DDD5; padding: 6px 14px; border-radius: 4px; background: #FFFFFF; cursor: pointer;">
                                Reset All Filters
                            </button>
                        </div>
                    `;
                    const emptyReset = document.getElementById('btnEmptyReset');
                    if (emptyReset) emptyReset.addEventListener('click', resetAllFilters);
                    renderPagination(1, 1);
                    return;
                }

                renderBooksGrid(books);
                renderPagination(page, totalPages);
            } else {
                renderErrorState(data.message || 'Unable to retrieve books.');
            }
        })
        .catch(err => {
            console.error('Error fetching catalog books:', err);
            renderErrorState('Network connection error while reaching the catalog service.');
        });
    }

    function renderErrorState(message) {
        const grid = document.getElementById('catalogGrid');
        if (!grid) return;
        grid.innerHTML = `
            <div style="grid-column: 1 / -1; text-align: center; padding: 48px; background: #FEF2F2; border: 1px solid #FCA5A5; border-radius: 8px; color: #B91C1C;">
                <p style="font-weight: 600; margin-bottom: 8px;">${escapeHtml(message)}</p>
                <button type="button" class="btn btn-secondary btn-sm" onclick="location.reload()" style="cursor: pointer; padding: 6px 12px; border-radius: 4px; border: 1px solid #FCA5A5;">
                    Retry Request
                </button>
            </div>
        `;
    }

    function renderBooksGrid(books) {
        const grid = document.getElementById('catalogGrid');
        if (!grid) return;

        grid.innerHTML = books.map(book => {
            const titleEsc = escapeHtml(book.title || 'Untitled');
            const authorEsc = escapeHtml(book.author || 'PSG iTech');
            const catEsc = escapeHtml(book.category || 'General');
            const deptEsc = escapeHtml(book.department || 'CSE');
            const yearEsc = book.publicationYear ? escapeHtml(book.publicationYear) : '';
            const coverUrl = book.coverImageUrl || `https://covers.openlibrary.org/b/isbn/${(book.isbn || '').replace(/[^0-9X]/gi, '')}-L.jpg`;

            // Calculate availability badge
            const avail = (book.inventory && typeof book.inventory.availableCopies === 'number')
                ? book.inventory.availableCopies
                : (book.availableCopies || 0);
            const total = (book.inventory && typeof book.inventory.totalActiveCopies === 'number')
                ? book.inventory.totalActiveCopies
                : (book.totalActiveCopies || 0);

            let badgeHtml = '';
            if (avail > 0) {
                badgeHtml = `<span class="badge badge-available" style="background: #E8F5EE; color: #159B50; border: 1px solid #A3E0BF; padding: 3px 8px; border-radius: 4px; font-size: 0.8rem; font-weight: 600;">Available (${avail} of ${total})</span>`;
            } else {
                badgeHtml = `<span class="badge badge-borrowed" style="background: #FEF3C7; color: #B45309; border: 1px solid #FDE68A; padding: 3px 8px; border-radius: 4px; font-size: 0.8rem; font-weight: 600;">All Copies Borrowed</span>`;
            }

            return `
                <div class="catalog-book-card" onclick="window.location.href='book-details.html?id=${book.bookId}'" title="${titleEsc}">
                    <div class="catalog-book-cover">
                        <img src="${escapeHtml(coverUrl)}" 
                             alt="${titleEsc}" 
                             loading="lazy"
                             onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';" />
                        <div class="book-cover-mock" style="display: none; width: 100%; height: 100%; background: linear-gradient(135deg, #203B60 0%, #102540 100%); flex-direction: column; justify-content: center; align-items: center; text-align: center; color: #FFFFFF; padding: 16px;">
                            <span style="font-size: 0.75rem; text-transform: uppercase; letter-spacing: 1px; color: #A3C1E8;">${deptEsc}</span>
                            <span style="font-family: 'Times New Roman', serif; font-size: 1.05rem; font-weight: bold; margin: 8px 0;">${titleEsc}</span>
                            <span style="font-size: 0.82rem; color: #E2DDD5;">${authorEsc}</span>
                        </div>
                    </div>
                    <div class="catalog-book-details">
                        <div>
                            <div class="catalog-book-title">${titleEsc}</div>
                            <div class="catalog-book-author">${authorEsc}</div>
                            <div class="catalog-book-meta-sub">
                                <span>${catEsc}</span> &bull; <span>${deptEsc}</span>${yearEsc ? ` &bull; <span>${yearEsc}</span>` : ''}
                            </div>
                        </div>
                        <div style="margin-top: 8px;">
                            ${badgeHtml}
                        </div>
                    </div>
                </div>
            `;
        }).join('');
    }

    /**
     * Renders removable filter pills in the single-row toolbar
     */
    function renderActiveFilterPills() {
        const container = document.getElementById('activeFilterPills');
        if (!container) return;

        const pills = [];

        if (state.keyword) {
            pills.push({ label: `Query: "${state.keyword}"`, clear: () => { state.keyword = ''; const s = document.getElementById('navSearchInput'); if (s) s.value = ''; } });
        }
        if (state.category && state.category !== 'ALL') {
            pills.push({ label: `Category: ${state.category}`, clear: () => { state.category = 'ALL'; const s = document.getElementById('filterCategorySelect'); if (s) s.value = 'ALL'; updateCategoryCardVisuals(); } });
        }
        if (state.author) {
            pills.push({ label: `Author: ${state.author}`, clear: () => { state.author = ''; const s = document.getElementById('filterAuthorInput'); if (s) s.value = ''; } });
        }
        if (state.publisher) {
            pills.push({ label: `Publisher: ${state.publisher}`, clear: () => { state.publisher = ''; const s = document.getElementById('filterPublisherInput'); if (s) s.value = ''; } });
        }
        if (state.department && state.department !== 'ALL') {
            pills.push({ label: `Dept: ${state.department}`, clear: () => { state.department = 'ALL'; const s = document.getElementById('filterDeptSelect'); if (s) s.value = 'ALL'; } });
        }
        if (state.yearFrom !== null || state.yearTo !== null) {
            const yrLabel = `Year: ${state.yearFrom || '...'} &ndash; ${state.yearTo || '...'}`;
            pills.push({ label: yrLabel, clear: () => { state.yearFrom = null; state.yearTo = null; const yf = document.getElementById('filterYearFrom'); const yt = document.getElementById('filterYearTo'); if (yf) yf.value = ''; if (yt) yt.value = ''; } });
        }
        if (state.availability && state.availability !== 'ALL') {
            const availText = state.availability === 'AVAILABLE' ? 'Available Only' : 'Unavailable Only';
            pills.push({ label: availText, clear: () => { state.availability = 'ALL'; const s = document.getElementById('filterAvailabilitySelect'); if (s) s.value = 'ALL'; } });
        }

        if (pills.length === 0) {
            container.innerHTML = '';
            return;
        }

        container.innerHTML = pills.map((p, idx) => `
            <span class="active-pill">
                <span>${p.label}</span>
                <button type="button" data-pill-idx="${idx}" title="Remove filter" aria-label="Remove filter">&times;</button>
            </span>
        `).join('');

        container.querySelectorAll('button[data-pill-idx]').forEach(btn => {
            const idx = parseInt(btn.getAttribute('data-pill-idx'), 10);
            btn.addEventListener('click', function (e) {
                e.stopPropagation();
                if (pills[idx] && typeof pills[idx].clear === 'function') {
                    pills[idx].clear();
                    state.page = 1;
                    fetchAndRenderBooks();
                }
            });
        });
    }

    /**
     * Renders server-side pagination controls
     */
    function renderPagination(currentPage, totalPages) {
        const container = document.getElementById('catalogPagination');
        if (!container) return;

        if (totalPages <= 1) {
            container.innerHTML = '';
            return;
        }

        let html = '';
        if (currentPage > 1) {
            html += `<button type="button" class="btn btn-secondary btn-sm" id="btnPagePrev" style="padding: 6px 12px; border: 1px solid #E2DDD5; border-radius: 4px; background: #FFFFFF; cursor: pointer;">&larr; Prev</button>`;
        }

        const maxPagesToShow = 5;
        let startPage = Math.max(1, currentPage - 2);
        let endPage = Math.min(totalPages, startPage + maxPagesToShow - 1);
        if (endPage - startPage < maxPagesToShow - 1) {
            startPage = Math.max(1, endPage - maxPagesToShow + 1);
        }

        for (let p = startPage; p <= endPage; p++) {
            const isCurrent = p === currentPage;
            html += `
                <button type="button" 
                        class="page-num-btn ${isCurrent ? 'active' : ''}" 
                        data-page="${p}"
                        style="padding: 6px 12px; border: 1px solid ${isCurrent ? '#203B60' : '#E2DDD5'}; border-radius: 4px; background: ${isCurrent ? '#203B60' : '#FFFFFF'}; color: ${isCurrent ? '#FFFFFF' : '#203B60'}; font-weight: ${isCurrent ? 'bold' : 'normal'}; cursor: pointer;">
                    ${p}
                </button>
            `;
        }

        if (currentPage < totalPages) {
            html += `<button type="button" class="btn btn-secondary btn-sm" id="btnPageNext" style="padding: 6px 12px; border: 1px solid #E2DDD5; border-radius: 4px; background: #FFFFFF; cursor: pointer;">Next &rarr;</button>`;
        }

        container.innerHTML = html;

        const prevBtn = document.getElementById('btnPagePrev');
        if (prevBtn) {
            prevBtn.addEventListener('click', () => {
                state.page--;
                fetchAndRenderBooks();
                window.scrollTo({ top: 300, behavior: 'smooth' });
            });
        }

        const nextBtn = document.getElementById('btnPageNext');
        if (nextBtn) {
            nextBtn.addEventListener('click', () => {
                state.page++;
                fetchAndRenderBooks();
                window.scrollTo({ top: 300, behavior: 'smooth' });
            });
        }

        container.querySelectorAll('button[data-page]').forEach(btn => {
            btn.addEventListener('click', function () {
                const targetPage = parseInt(this.getAttribute('data-page'), 10);
                if (targetPage !== state.page) {
                    state.page = targetPage;
                    fetchAndRenderBooks();
                    window.scrollTo({ top: 300, behavior: 'smooth' });
                }
            });
        });
    }

    function escapeHtml(str) {
        if (str === null || str === undefined) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

})();
