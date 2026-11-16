/**
 * PSG iTech Library - Feature 2: Book Management Client Module
 * Handles Catalogue Browsing, Dynamic Search, Book Details,
 * and Staff Copy Management / Archival Workflows.
 */

document.addEventListener('DOMContentLoaded', function () {
    if (document.getElementById('catalogGrid')) {
        initBrowseCatalog();
    }
    if (document.getElementById('shelfTrending') || document.getElementById('shelfFeatured')) {
        initHomepageShelves();
    }
    if (document.getElementById('bookDetailsContainer')) {
        initBookDetailsPage();
    }
    if (document.getElementById('manageBooksTableBody')) {
        initManageBooksWorkspace();
    }
});

/**
 * Utility: Debounce function for smooth search typing
 */
function debounce(func, wait) {
    let timeout;
    return function (...args) {
        clearTimeout(timeout);
        timeout = setTimeout(() => func.apply(this, args), wait);
    };
}

/**
 * Utility: Safe HTML Escaping to prevent XSS
 */
function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

/**
 * Helper to check current logged in user role from cached session
 */
function getCurrentUserRole() {
    try {
        const stored = sessionStorage.getItem('psgitech_user') || localStorage.getItem('psgitech_user');
        if (stored) {
            const user = JSON.parse(stored);
            return user.role || 'STUDENT';
        }
    } catch (e) {}
    return 'STUDENT';
}

function isStaffUser() {
    const role = getCurrentUserRole();
    return role === 'LIBRARIAN' || role === 'ADMIN';
}

/**
 * Renders a consistent book cover element with automatic Open Library API & typographic fallback.
 */
function renderBookCoverHtml(book, customClass = '') {
    const title = escapeHtml(book.title || 'Library Book');
    const author = escapeHtml(book.author || 'PSG iTech');
    const dept = escapeHtml(book.department || 'CSE');
    const coverUrl = book.coverImageUrl || `https://covers.openlibrary.org/b/isbn/${(book.isbn || '').replace(/[^0-9X]/gi, '')}-L.jpg`;

    return `
        <div class="book-cover-container ${customClass}">
            <img src="${escapeHtml(coverUrl)}" 
                 alt="${title}" 
                 class="book-cover-img"
                 loading="lazy"
                 onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';"
                 style="width: 100%; height: 100%; object-fit: cover; border-radius: 4px;">
            <div class="book-cover-mock" style="display: none; background: linear-gradient(135deg, #203B60 0%, #102540 100%);">
                <span class="mock-dept">${dept}</span>
                <span class="mock-title">${title}</span>
                <span class="mock-author">${author}</span>
            </div>
        </div>
    `;
}

/**
 * Formats availability badge according to Section 7 specs:
 * If available > 0: "Available (X of Y)" (Green)
 * If available == 0: "Currently Unavailable" (Amber)
 */
function renderAvailabilityBadgeHtml(book) {
    if (book.archived) {
        return `<span class="badge badge-copy-archived">Archived</span>`;
    }
    const avail = (book.inventory && typeof book.inventory.availableCopies === 'number') 
        ? book.inventory.availableCopies 
        : (book.availableCopies || 0);
    const total = (book.inventory && typeof book.inventory.totalActiveCopies === 'number')
        ? book.inventory.totalActiveCopies
        : (book.totalActiveCopies || 0);

    if (avail > 0) {
        return `<span class="badge badge-available">Available (${avail} of ${total})</span>`;
    } else {
        const waiting = book.waitingCount || (book.inventory && book.inventory.waitingCount) || 0;
        const waitingText = waiting > 0 ? ` &bull; Waiting: ${waiting}` : '';
        return `<span class="badge badge-borrowed" style="background-color: #FEF3C7; color: #B45309; border: 1px solid #FDE68A;">All Copies Borrowed${waitingText}</span>`;
    }
}

/**
 * =============================================================================
 * 1. Browse Books Catalog (browse.html)
 * Dynamic Search, Category/Dept/Availability Filtering, Debounced Inputs
 * =============================================================================
 */
function initBrowseCatalog() {
    const grid = document.getElementById('catalogGrid');
    const searchInput = document.getElementById('catalogSearchInput');
    const searchTypeSelect = document.getElementById('catalogSearchType');
    const categorySelect = document.getElementById('catalogCategoryFilter');
    const deptSelect = document.getElementById('catalogDeptFilter');
    const availSelect = document.getElementById('catalogAvailabilityFilter');
    const archivedCheckbox = document.getElementById('catalogArchivedFilter');
    const archivedContainer = document.getElementById('archivedFilterContainer');
    const countBadge = document.getElementById('catalogCountBadge');

    // Show archived filter only to staff
    if (archivedContainer) {
        archivedContainer.style.display = isStaffUser() ? 'inline-flex' : 'none';
    }

    // Parse URL query parameters if navigated from search bar or category card
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('q') && searchInput) {
        searchInput.value = urlParams.get('q');
    }
    if (urlParams.has('type') && searchTypeSelect) {
        searchTypeSelect.value = urlParams.get('type');
    }
    if (urlParams.has('category') && categorySelect) {
        categorySelect.value = urlParams.get('category');
    }
    if (urlParams.has('department') && deptSelect) {
        deptSelect.value = urlParams.get('department');
    }

    function fetchAndRenderCatalog() {
        if (!grid) return;
        grid.innerHTML = `
            <div style="grid-column: 1 / -1; text-align: center; padding: 48px; color: var(--text-secondary);">
                <div style="font-size: 1.1rem; margin-bottom: 8px;">Searching library catalog...</div>
            </div>
        `;

        const params = new URLSearchParams();
        if (searchInput && searchInput.value.trim()) {
            params.append('keyword', searchInput.value.trim());
        }
        if (searchTypeSelect && searchTypeSelect.value) {
            params.append('searchType', searchTypeSelect.value);
        }
        if (categorySelect && categorySelect.value && categorySelect.value !== 'ALL') {
            params.append('category', categorySelect.value);
        }
        if (deptSelect && deptSelect.value && deptSelect.value !== 'ALL') {
            params.append('department', deptSelect.value);
        }
        if (availSelect && availSelect.value && availSelect.value !== 'ALL') {
            params.append('availability', availSelect.value);
        }
        if (archivedCheckbox && archivedCheckbox.checked && isStaffUser()) {
            params.append('includeArchived', 'true');
        }

        fetch(`books/search?${params.toString()}`, {
            headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
        })
        .then(res => res.json())
        .then(data => {
            if (!data.success || !data.books || data.books.length === 0) {
                if (countBadge) countBadge.textContent = '0 Books Found';
                grid.innerHTML = `
                    <div style="grid-column: 1 / -1; text-align: center; padding: 60px 20px; background: var(--background-warm); border: 1px solid var(--border); border-radius: 6px;">
                        <h3 style="color: var(--primary); margin-bottom: 8px;">No Books Found</h3>
                        <p class="text-secondary" style="max-width: 480px; margin: 0 auto 16px;">We couldn't find any catalog items matching your current search criteria. Try adjusting your keywords or clearing the category and availability filters.</p>
                        <button class="btn btn-secondary btn-sm" onclick="resetCatalogFilters()">Reset All Filters</button>
                    </div>
                `;
                return;
            }

            if (countBadge) {
                countBadge.textContent = `${data.count || data.books.length} Books Found`;
            }

            grid.innerHTML = data.books.map(book => `
                <div class="book-card" onclick="window.location.href='book-details.html?id=${book.bookId}'" style="cursor: pointer;">
                    ${renderBookCoverHtml(book)}
                    <div class="book-info">
                        <div class="book-title" title="${escapeHtml(book.title)}">${escapeHtml(book.title)}</div>
                        <div class="author-name" title="${escapeHtml(book.author)}">${escapeHtml(book.author)}</div>
                        <div style="font-size: 0.82rem; color: var(--text-secondary); margin: 4px 0;">
                            <span>${escapeHtml(book.category)}</span> &bull; <span>${escapeHtml(book.department)}</span>
                        </div>
                        <div class="book-meta" style="margin-top: 6px;">
                            ${renderAvailabilityBadgeHtml(book)}
                        </div>
                    </div>
                </div>
            `).join('');
        })
        .catch(err => {
            console.error('Error loading catalogue:', err);
            grid.innerHTML = `
                <div style="grid-column: 1 / -1; text-align: center; padding: 48px; background: #FDE8E8; border: 1px solid #F8B4B4; border-radius: 6px; color: var(--danger);">
                    <p style="font-weight: bold; margin-bottom: 8px;">Unable to load book catalogue from server.</p>
                    <button class="btn btn-secondary btn-sm" onclick="location.reload()">Retry Connection</button>
                </div>
            `;
        });
    }

    const debouncedSearch = debounce(fetchAndRenderCatalog, 300);

    if (searchInput) searchInput.addEventListener('input', debouncedSearch);
    if (searchTypeSelect) searchTypeSelect.addEventListener('change', fetchAndRenderCatalog);
    if (categorySelect) categorySelect.addEventListener('change', fetchAndRenderCatalog);
    if (deptSelect) deptSelect.addEventListener('change', fetchAndRenderCatalog);
    if (availSelect) availSelect.addEventListener('change', fetchAndRenderCatalog);
    if (archivedCheckbox) archivedCheckbox.addEventListener('change', fetchAndRenderCatalog);

    window.resetCatalogFilters = function () {
        if (searchInput) searchInput.value = '';
        if (searchTypeSelect) searchTypeSelect.value = 'all';
        if (categorySelect) categorySelect.value = 'ALL';
        if (deptSelect) deptSelect.value = 'ALL';
        if (availSelect) availSelect.value = 'ALL';
        if (archivedCheckbox) archivedCheckbox.checked = false;
        fetchAndRenderCatalog();
    };

    fetchAndRenderCatalog();
}

/**
 * =============================================================================
 * 2. Homepage Real Books Shelves (index.html)
 * Replaces hardcoded shelves with live database records
 * =============================================================================
 */
function initHomepageShelves() {
    const trendingShelf = document.getElementById('shelfTrending');
    const recentShelf = document.getElementById('shelfRecent');
    const studentShelf = document.getElementById('studentTrending');

    if (trendingShelf || recentShelf || studentShelf) {
        fetch('books?featured=true&limit=12', {
            headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
        })
        .then(res => res.json())
        .then(data => {
            if (data.success && data.books && data.books.length > 0) {
                const books = data.books;
                const renderCard = (b) => `
                    <div class="book-card" onclick="window.location.href='book-details.html?id=${b.bookId}'" style="cursor: pointer;">
                        ${renderBookCoverHtml(b)}
                        <div class="book-info">
                            <div class="book-title" title="${escapeHtml(b.title)}">${escapeHtml(b.title)}</div>
                            <div class="author-name">${escapeHtml(b.author)}</div>
                            <div class="book-meta">
                                ${renderAvailabilityBadgeHtml(b)}
                            </div>
                        </div>
                    </div>
                `;

                if (trendingShelf) {
                    trendingShelf.innerHTML = books.slice(0, 6).map(renderCard).join('');
                }
                if (recentShelf) {
                    recentShelf.innerHTML = books.slice(4).map(renderCard).join('');
                }
                if (studentShelf) {
                    studentShelf.innerHTML = books.slice(0, 4).map(renderCard).join('');
                }
            }
        })
        .catch(e => console.warn('Homepage featured books query notice:', e));
    }
}

/**
 * =============================================================================
 * 3. Book Details & Physical Copies Panel (book-details.html)
 * Displays bibliographic metadata and staff copy controls
 * =============================================================================
 */
function initBookDetailsPage() {
    const container = document.getElementById('bookDetailsContainer');
    if (!container) return;

    const urlParams = new URLSearchParams(window.location.search);
    const bookId = urlParams.get('id');

    if (!bookId) {
        container.innerHTML = `
            <div class="alert alert-danger">
                Book ID missing. Please return to the <a href="browse.html" style="font-weight: bold; margin-left: 6px;">Browse Catalogue</a>.
            </div>
        `;
        return;
    }

    fetch(`books/details?id=${encodeURIComponent(bookId)}`, {
        headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
    })
    .then(async res => {
        const data = await res.json().catch(() => ({}));
        if (!res.ok || !data.success || !data.book) {
            throw new Error(data.message || 'Book could not be retrieved.');
        }
        if (data.loanPolicy) {
            data.book.loanPolicy = data.loanPolicy;
        }
        renderBookDetailsView(data.book, data.isStaff);
        loadSimilarBooks(bookId);
    })
    .catch(err => {
        container.innerHTML = `
            <div style="padding: 40px; text-align: center; background: var(--background-warm); border: 1px solid var(--border); border-radius: 6px;">
                <h3 style="color: var(--danger); margin-bottom: 8px;">Book Unavailable</h3>
                <p class="text-secondary">${escapeHtml(err.message)}</p>
                <a href="browse.html" class="btn btn-primary btn-sm" style="margin-top: 14px;">Return to Catalogue</a>
            </div>
        `;
    });
}

function renderBookDetailsView(book, isStaff) {
    const container = document.getElementById('bookDetailsContainer');
    if (!container) return;

    const avail = (book.inventory && typeof book.inventory.availableCopies === 'number') 
        ? book.inventory.availableCopies 
        : (book.availableCopies || 0);
    const total = (book.inventory && typeof book.inventory.totalActiveCopies === 'number')
        ? book.inventory.totalActiveCopies
        : (book.totalActiveCopies || 0);

    const copies = (book.inventory && book.inventory.copies) ? book.inventory.copies : [];

    let staffControlsHtml = '';
    if (isStaff) {
        staffControlsHtml = `
            <div style="background-color: var(--background-warm); border: 1px solid var(--border); padding: 18px 20px; border-radius: 6px; margin-top: 24px;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 10px;">
                    <div>
                        <strong style="color: var(--primary); font-size: 1.05rem;">Staff Management Actions</strong>
                        <span class="badge badge-active" style="margin-left: 8px;">Librarian / Admin Desk</span>
                    </div>
                    <div style="display: flex; gap: 8px; flex-wrap: wrap;">
                        <button class="btn btn-secondary btn-sm" onclick="openEditBookModal(${book.bookId})">
                            Edit Book Info
                        </button>
                        <button class="btn btn-secondary btn-sm" onclick="openAddCopiesModal(${book.bookId})">
                            + Add Physical Copies
                        </button>
                        ${!book.archived ? `
                            <button class="btn btn-secondary btn-sm" style="color: var(--danger); border-color: var(--danger);" onclick="confirmArchiveBook(${book.bookId})">
                                Archive Book Title
                            </button>
                        ` : `
                            <span class="badge badge-copy-archived">Book Already Archived</span>
                        `}
                    </div>
                </div>
            </div>

            <!-- Physical Copies Management Table (Librarian/Admin only) -->
            <div style="margin-top: 36px;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px;">
                    <h3>Physical Copy Management (${copies.length} Copies)</h3>
                    <button class="btn btn-primary btn-sm" onclick="openAddCopiesModal(${book.bookId})">+ Add Copies</button>
                </div>
                <div class="table-responsive">
                    <table class="library-table">
                        <thead>
                            <tr>
                                <th>Copy ID</th>
                                <th>Copy Code</th>
                                <th>Status</th>
                                <th>Added Date</th>
                                <th style="text-align: right;">Action</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${copies.length === 0 ? `
                                <tr>
                                    <td colspan="5" style="text-align: center; color: var(--text-secondary); padding: 24px;">
                                        No physical copies registered for this title yet.
                                    </td>
                                </tr>
                            ` : copies.map(copy => `
                                <tr>
                                    <td>#${copy.copyId}</td>
                                    <td><strong style="font-family: monospace; font-size: 1rem;">${escapeHtml(copy.copyCode)}</strong></td>
                                    <td>
                                        <span class="badge badge-copy-${(copy.status || 'AVAILABLE').toLowerCase().replace('_', '-')}">
                                            ${escapeHtml(copy.status)}
                                        </span>
                                    </td>
                                    <td>${copy.createdAt ? String(copy.createdAt).substring(0, 10) : '—'}</td>
                                    <td style="text-align: right;">
                                        ${copy.status === 'AVAILABLE' ? `
                                            <button class="btn btn-secondary btn-sm" style="color: var(--danger); border-color: var(--danger); padding: 4px 8px; font-size: 0.82rem;" 
                                                    onclick="archiveCopyItem(${copy.copyId}, '${escapeHtml(copy.copyCode)}')">
                                                Archive Copy
                                            </button>
                                        ` : `
                                            <span class="text-secondary" style="font-size: 0.85rem;">—</span>
                                        `}
                                    </td>
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                </div>
            </div>
        `;
    }

    container.innerHTML = `
        <div style="margin-bottom: 20px;">
            <a href="browse.html" class="text-secondary" style="font-size: 0.95rem;">&larr; Back to Catalog Browse</a>
        </div>

        <div class="book-details-layout">
            <!-- Left: Cover Card -->
            <div>
                <div class="book-detail-cover-card">
                    <img src="${escapeHtml(book.coverImageUrl || `https://covers.openlibrary.org/b/isbn/${(book.isbn || '').replace(/[^0-9X]/gi, '')}-L.jpg`)}" 
                         alt="${escapeHtml(book.title)}"
                         class="book-detail-cover-img"
                         onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
                    <div class="book-cover-mock" style="display: none; height: 320px; background: linear-gradient(135deg, #203B60 0%, #102540 100%);">
                        <span class="mock-dept">${escapeHtml(book.department)}</span>
                        <span class="mock-title">${escapeHtml(book.title)}</span>
                        <span class="mock-author">${escapeHtml(book.author)}</span>
                    </div>

                    <div style="margin-top: 18px;">
                        ${renderAvailabilityBadgeHtml(book)}
                    </div>
                </div>
            </div>

            <!-- Right: Bibliographic Info & Availability -->
            <div>
                <div style="display: flex; gap: 8px; margin-bottom: 10px; flex-wrap: wrap;">
                    <span class="badge badge-role">${escapeHtml(book.category)}</span>
                    <span class="badge badge-active">${escapeHtml(book.department)}</span>
                    ${book.archived ? '<span class="badge badge-copy-archived">ARCHIVED TITLE</span>' : ''}
                    ${book.loanPolicy && book.loanPolicy.isHighDemand ? `<span class="badge badge-high-demand" title="High-demand item: ${book.loanPolicy.loanDays}-day loan, no renewals">🔥 ${escapeHtml(book.loanPolicy.label || 'High Demand')} (${book.loanPolicy.loanDays} Days)</span>` : (book.loanPolicy ? `<span class="badge badge-standard-demand">Standard Loan (14 Days)</span>` : '')}
                </div>

                <h1 style="margin-bottom: 6px; line-height: 1.25;">${escapeHtml(book.title)}</h1>
                <h3 class="text-secondary" style="font-weight: normal; margin-bottom: 20px;">by <strong>${escapeHtml(book.author)}</strong></h3>

                <!-- Synopsis -->
                <div style="margin-bottom: 24px;">
                    <h4 style="margin-bottom: 6px;">Overview &amp; Syllabus Description</h4>
                    <p style="line-height: 1.7; color: var(--text-primary);">
                        ${escapeHtml(book.description || 'Comprehensive official university reference and textbook covering theoretical foundations, rigorous derivations, and engineering applications.')}
                    </p>
                </div>

                <!-- Metadata Table -->
                <table class="book-meta-table">
                    <tbody>
                        <tr>
                            <td class="meta-label">ISBN</td>
                            <td><strong style="font-family: monospace;">${escapeHtml(book.isbn)}</strong></td>
                        </tr>
                        <tr>
                            <td class="meta-label">Publisher</td>
                            <td>${escapeHtml(book.publisher || 'Academic Press')}</td>
                        </tr>
                        <tr>
                            <td class="meta-label">Edition</td>
                            <td>${escapeHtml(book.edition || 'Standard Edition')}</td>
                        </tr>
                        <tr>
                            <td class="meta-label">Publication Year</td>
                            <td>${book.publicationYear || '—'}</td>
                        </tr>
                        <tr>
                            <td class="meta-label">Shelf Availability</td>
                            <td>
                                <strong>${avail}</strong> copy(ies) available on library racks out of <strong>${total}</strong> active physical copies.${isStaff && book.inventory && book.inventory.allocatedCopies > 0 ? ` <span class="badge badge-copy-allocated" style="margin-left: 6px;">${book.inventory.allocatedCopies} on Hold</span>` : ''}
                            </td>
                        </tr>
                        <tr>
                            <td class="meta-label">Borrowing Policy</td>
                            <td>
                                ${book.loanPolicy && book.loanPolicy.isHighDemand 
                                    ? `<strong style="color: #856404;">🔥 ${escapeHtml(book.loanPolicy.label || 'High Demand Policy')}</strong> &bull; ${book.loanPolicy.loanDays}-day loan period (Renewals disabled due to high queue pressure)` 
                                    : `<strong>Standard Policy</strong> &bull; 14-day loan period (Renewals permitted)`}
                            </td>
                        </tr>
                    </tbody>
                </table>

                ${staffControlsHtml}

                <div id="reservationSection"></div>
            </div>
        </div>
    `;

    // Cache current book for modals
    window._currentViewingBook = book;

    // Load dynamic reservation queue status
    loadBookReservationStatus(book, avail, total);
}

/**
 * Feature 4: Load reservation status & waiting queue for Book Details page
 */
function loadBookReservationStatus(book, avail, total) {
    const sec = document.getElementById('reservationSection');
    if (!sec || book.archived) return;

    if (avail > 0) {
        sec.innerHTML = `
            <div class="info-card" style="background-color: #E8F8F0; border-left: 4px solid var(--success); padding: 12px 16px; margin-top: 20px;">
                <strong style="color: var(--success);">&#10003; Copies Available:</strong> Copies of this textbook are currently available on the shelf for borrowing at the library desk. Reservations are only open when all copies are checked out.
            </div>
        `;
        return;
    }

    if (total === 0) {
        sec.innerHTML = `
            <div class="info-card" style="background-color: #FEF3C7; border-left: 4px solid #D97706; padding: 12px 16px; margin-top: 20px;">
                <strong style="color: #B45309;">No Active Copies:</strong> This book has no physical copies in active circulation to reserve.
            </div>
        `;
        return;
    }

    // Call /reservations?bookId=...
    fetch('reservations?bookId=' + book.bookId)
        .then(res => {
            if (res.status === 401) {
                return { isGuest: true };
            }
            return res.json();
        })
        .then(data => {
            if (data.isGuest) {
                sec.innerHTML = `
                    <div class="info-card" style="background-color: #F8F6F0; border-left: 4px solid var(--primary); padding: 14px 18px; margin-top: 20px;">
                        <h4 style="margin: 0 0 4px 0; color: var(--primary);">All Copies Currently Borrowed</h4>
                        <p class="text-secondary" style="margin: 0 0 10px 0;">Sign in with your student credentials to join the FIFO reservation queue.</p>
                        <a href="login.html?redirect=book-details.html?id=${book.bookId}" class="btn btn-secondary btn-sm">Log In to Reserve</a>
                    </div>
                `;
                return;
            }

            if (!data.success) return;

            const waitingCount = data.waitingCount || 0;
            const waitingLabel = waitingCount === 1 ? '1 student' : `${waitingCount} students`;

            if (data.queue) {
                // Staff view
                sec.innerHTML = `
                    <div class="info-card" style="background-color: #F8F6F0; border-left: 4px solid var(--primary); padding: 14px 18px; margin-top: 20px;">
                        <h4 style="margin: 0 0 4px 0; color: var(--primary);">Reservation Queue: ${waitingCount} Waiting</h4>
                        <p class="text-secondary" style="margin: 0 0 10px 0;">All physical copies are checked out. View and manage the queue in the circulation desk.</p>
                        <a href="borrowing-management.html" class="btn btn-secondary btn-sm">Open Circulation Desk</a>
                    </div>
                `;
            } else if (data.currentlyBorrowedByMe) {
                sec.innerHTML = `
                    <div class="info-card" style="background-color: #FEF3C7; border-left: 4px solid #D97706; padding: 14px 18px; margin-top: 20px;">
                        <strong style="color: #B45309;">Currently Borrowed by You:</strong> You have an active loan for a copy of this book. Students cannot hold both an active loan and a reservation for the same title.
                    </div>
                `;
            } else if (data.myReservation && data.myReservation.status === 'ALLOCATED') {
                const copyCode = (data.myReservation.allocation && (data.myReservation.allocation.copyCode || data.myReservation.allocation.copyIdentifier)) || data.myReservation.copyCode || '';
                const expiresAt = (data.myReservation.allocation && data.myReservation.allocation.expiresAt) || data.myReservation.allocationExpiresAt || '';
                sec.innerHTML = `
                    <div class="info-card" style="background-color: #E8F8F0; border-left: 4px solid #159B50; padding: 16px 20px; margin-top: 20px;">
                        <div style="font-weight: bold; color: #159B50; font-size: 1.15rem; margin-bottom: 6px;">
                            ✓ Allocated to You - Ready for Collection!
                        </div>
                        <p class="text-secondary" style="margin: 0 0 10px 0;">
                            A physical copy (<code>${escapeHtml(copyCode)}</code>) has been held for you at the library circulation desk.
                            Please collect from the desk by <strong>${escapeHtml(expiresAt ? expiresAt.substring(0, 16) : '24 Hours')}</strong>.
                        </p>
                        <a href="my-reservations.html" class="btn btn-primary btn-sm">View in My Reservations</a>
                    </div>
                `;
            } else if (data.myReservation) {
                const pos = data.myReservation.queuePosition || 1;
                sec.innerHTML = `
                    <div class="info-card" style="background-color: #F8F6F0; border-left: 4px solid var(--primary); padding: 14px 18px; margin-top: 20px;">
                        <div style="font-weight: bold; color: var(--primary); font-size: 1.1rem; margin-bottom: 4px;">
                            &#10004; Reserved (Your Queue Position: #${pos})
                        </div>
                        <p class="text-secondary" style="margin: 0 0 12px 0;">
                            Waiting Queue: <strong>${waitingLabel}</strong> in line. You will be allocated a copy as soon as a student returns one.
                        </p>
                        <a href="my-reservations.html" class="btn btn-secondary btn-sm">View in My Reservations</a>
                    </div>
                `;
            } else {
                // Eligible student: show "Reserve Book (Join Queue)" button
                sec.innerHTML = `
                    <div class="info-card" style="background-color: #F8F6F0; border-left: 4px solid var(--primary); padding: 16px 20px; margin-top: 20px;">
                        <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px;">
                            <div>
                                <h4 style="margin: 0 0 4px 0; color: var(--primary);">All Copies Currently Borrowed</h4>
                                <p class="text-secondary" style="margin: 0; font-size: 0.92rem;">
                                    Waiting Queue: <strong>${waitingLabel}</strong> in FIFO order
                                </p>
                            </div>
                            <button id="btnReserveBook" class="btn btn-primary" onclick="handleReserveBookClick(${book.bookId})">
                                Reserve Book (Join Queue)
                            </button>
                        </div>
                        <div id="reserveMsgContainer" style="margin-top: 8px;"></div>
                    </div>
                `;
            }
        })
        .catch(err => {
            console.error('Error checking reservation status:', err);
        });
}

window.handleReserveBookClick = async function(bookId) {
    const btn = document.getElementById('btnReserveBook');
    const msgBox = document.getElementById('reserveMsgContainer');
    if (btn) {
        btn.disabled = true;
        btn.textContent = 'Reserving...';
    }
    if (msgBox) msgBox.innerHTML = '';

    try {
        const resp = await fetch('reservations', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ bookId: bookId })
        });
        const result = await resp.json();

        if (resp.status === 201 && result.success) {
            const data = result.data || {};
            const pos = data.queuePosition || 1;
            const total = data.totalWaitingInQueue || pos;
            const waitingLabel = total === 1 ? '1 student' : `${total} students`;

            const sec = document.getElementById('reservationSection');
            if (sec) {
                sec.innerHTML = `
                    <div class="info-card" style="background-color: #F8F6F0; border-left: 4px solid var(--primary); padding: 14px 18px; margin-top: 20px;">
                        <div style="font-weight: bold; color: var(--primary); font-size: 1.1rem; margin-bottom: 4px;">
                            &#10004; Reserved (Your Queue Position: #${pos})
                        </div>
                        <p class="text-secondary" style="margin: 0 0 12px 0;">
                            Waiting Queue: <strong>${waitingLabel}</strong> in line. You will be allocated a copy as soon as a student returns one.
                        </p>
                        <a href="my-reservations.html" class="btn btn-secondary btn-sm">View in My Reservations</a>
                    </div>
                `;
            }
        } else {
            if (msgBox) {
                msgBox.innerHTML = `<div style="color: var(--danger); font-size: 0.92rem; margin-top: 6px;">${escapeHtml(result.message || 'Could not place reservation.')}</div>`;
            } else {
                alert(result.message || 'Could not place reservation.');
            }
            if (btn) {
                btn.disabled = false;
                btn.textContent = 'Reserve Book (Join Queue)';
            }
        }
    } catch (e) {
        console.error('Error reserving book:', e);
        if (msgBox) {
            msgBox.innerHTML = `<div style="color: var(--danger); font-size: 0.92rem; margin-top: 6px;">Network error while placing reservation. Please try again.</div>`;
        }
        if (btn) {
            btn.disabled = false;
            btn.textContent = 'Reserve Book (Join Queue)';
        }
    }
};

/**
 * Staff Action: Archive Single Copy
 */
window.archiveCopyItem = function (copyId, copyCode) {
    if (!confirm(`Are you sure you want to archive physical copy "${copyCode}"? It will be permanently retired from circulation.`)) {
        return;
    }

    const formData = new URLSearchParams();
    formData.append('target', 'copy');
    formData.append('copyId', copyId);

    fetch('books/archive', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'X-Requested-With': 'XMLHttpRequest'
        },
        body: formData.toString()
    })
    .then(async res => {
        const data = await res.json().catch(() => ({}));
        if (res.ok && data.success) {
            alert(data.message || 'Copy archived successfully.');
            location.reload();
        } else {
            alert(data.message || 'Failed to archive physical copy.');
        }
    })
    .catch(err => {
        alert('Network error communicating with library server.');
    });
};

/**
 * Staff Action: Archive Entire Book Title
 */
window.confirmArchiveBook = function (bookId) {
    if (!confirm('Are you sure you want to archive this ENTIRE book title? All active available physical copies will be marked as ARCHIVED. This action cannot proceed if any copy is currently BORROWED or ALLOCATED.')) {
        return;
    }

    const formData = new URLSearchParams();
    formData.append('target', 'book');
    formData.append('bookId', bookId);

    fetch('books/archive', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            'X-Requested-With': 'XMLHttpRequest'
        },
        body: formData.toString()
    })
    .then(async res => {
        const data = await res.json().catch(() => ({}));
        if (res.ok && data.success) {
            alert(data.message || 'Book and copies archived successfully.');
            location.reload();
        } else {
            alert(data.message || 'Failed to archive book.');
        }
    })
    .catch(() => alert('Network error communicating with library server.'));
};

/**
 * Staff Action: Open Add Copies Modal
 */
window.openAddCopiesModal = function (bookId) {
    const modal = document.getElementById('addCopiesModal');
    const inputId = document.getElementById('addCopiesBookId');
    if (modal && inputId) {
        inputId.value = bookId;
        modal.classList.add('show');
    }
};

/**
 * Staff Action: Open Edit Book Modal
 */
window.openEditBookModal = function (bookId) {
    const modal = document.getElementById('editBookModal');
    const book = window._currentViewingBook;
    if (!modal || !book) return;

    document.getElementById('editBookId').value = book.bookId;
    document.getElementById('editTitle').value = book.title || '';
    document.getElementById('editAuthor').value = book.author || '';
    document.getElementById('editIsbn').value = book.isbn || '';
    document.getElementById('editPublisher').value = book.publisher || '';
    document.getElementById('editEdition').value = book.edition || '';
    document.getElementById('editYear').value = book.publicationYear || '';
    document.getElementById('editCategory').value = book.category || 'Computer Science';
    document.getElementById('editDepartment').value = book.department || 'CSE';
    document.getElementById('editDescription').value = book.description || '';
    document.getElementById('editCoverUrl').value = book.coverImageUrl || '';

    modal.classList.add('show');
};

/**
 * =============================================================================
 * 4. Librarian & Admin Workspace (manage-books.html)
 * =============================================================================
 */
function initManageBooksWorkspace() {
    const tbody = document.getElementById('manageBooksTableBody');
    const searchInput = document.getElementById('manageSearchInput');
    const countBadge = document.getElementById('manageBooksCountBadge');

    function loadWorkspaceBooks() {
        if (!tbody) return;
        tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; padding: 24px;">Loading library books...</td></tr>`;

        const q = searchInput ? searchInput.value.trim() : '';
        const url = q ? `books/search?keyword=${encodeURIComponent(q)}&includeArchived=true` : `books?includeArchived=true`;

        fetch(url, {
            headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
        })
        .then(res => res.json())
        .then(data => {
            if (!data.success || !data.books || data.books.length === 0) {
                tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; padding: 28px; color: var(--text-secondary);">No books found. Click "+ Add New Book" above to register a catalog accession.</td></tr>`;
                if (countBadge) countBadge.textContent = '0 Books';
                return;
            }

            if (countBadge) countBadge.textContent = `${data.books.length} Books Registered`;

            tbody.innerHTML = data.books.map(b => {
                const avail = (b.inventory && typeof b.inventory.availableCopies === 'number') ? b.inventory.availableCopies : (b.availableCopies || 0);
                const total = (b.inventory && typeof b.inventory.totalActiveCopies === 'number') ? b.inventory.totalActiveCopies : (b.totalActiveCopies || 0);

                return `
                    <tr style="${b.archived ? 'opacity: 0.65; background-color: #F8F9FA;' : ''}">
                        <td>#${b.bookId}</td>
                        <td>
                            <a href="book-details.html?id=${b.bookId}" style="font-weight: bold; color: var(--primary);">
                                ${escapeHtml(b.title)}
                            </a>
                            <div class="text-secondary" style="font-size: 0.85rem;">by ${escapeHtml(b.author)}</div>
                        </td>
                        <td><code style="font-family: monospace;">${escapeHtml(b.isbn)}</code></td>
                        <td>
                            <span class="badge badge-role">${escapeHtml(b.category)}</span>
                            <span class="badge badge-active" style="margin-left: 4px;">${escapeHtml(b.department)}</span>
                        </td>
                        <td>
                            <strong>${avail}</strong> / ${total} available
                        </td>
                        <td>
                            ${b.archived ? '<span class="badge badge-copy-archived">Archived</span>' : '<span class="badge badge-available">Active</span>'}
                        </td>
                        <td style="text-align: right;">
                            <div style="display: flex; gap: 4px; justify-content: flex-end;">
                                <a href="book-details.html?id=${b.bookId}" class="btn btn-secondary btn-sm" style="padding: 4px 8px; font-size: 0.82rem;">Details</a>
                                ${!b.archived ? `
                                    <button class="btn btn-secondary btn-sm" style="padding: 4px 8px; font-size: 0.82rem;" onclick="openAddCopiesModal(${b.bookId})">+ Copies</button>
                                    <button class="btn btn-secondary btn-sm" style="color: var(--danger); border-color: var(--danger); padding: 4px 8px; font-size: 0.82rem;" onclick="confirmArchiveBook(${b.bookId})">Archive</button>
                                ` : ''}
                            </div>
                        </td>
                    </tr>
                `;
            }).join('');
        })
        .catch(err => {
            console.error('Workspace load error:', err);
            tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--danger); padding: 24px;">Failed to load books from server.</td></tr>`;
        });
    }

    if (searchInput) {
        searchInput.addEventListener('input', debounce(loadWorkspaceBooks, 300));
    }

    loadWorkspaceBooks();

    // Wire Add Book Form
    const addForm = document.getElementById('addBookForm');
    const addAlert = document.getElementById('addBookAlert');
    if (addForm) {
        addForm.addEventListener('submit', function (e) {
            e.preventDefault();

            const title = document.getElementById('newTitle').value.trim();
            const author = document.getElementById('newAuthor').value.trim();
            const isbn = document.getElementById('newIsbn').value.trim();
            const publisher = document.getElementById('newPublisher').value.trim();
            const edition = document.getElementById('newEdition').value.trim();
            const publicationYear = document.getElementById('newYear').value.trim();
            const category = document.getElementById('newCategory').value;
            const department = document.getElementById('newDepartment').value;
            const description = document.getElementById('newDescription').value.trim();
            const coverImageUrl = document.getElementById('newCoverUrl').value.trim();
            const replacementPrice = document.getElementById('newReplacementPrice')?.value?.trim();
            const initialCopies = document.getElementById('newInitialCopies').value.trim();

            if (!title || !author || !isbn || !publisher || !publicationYear || !category || !department) {
                showModalAlert(addAlert, 'danger', 'All mandatory fields (*) must be completed.');
                return;
            }

            const formData = new URLSearchParams();
            formData.append('title', title);
            formData.append('author', author);
            formData.append('isbn', isbn);
            formData.append('publisher', publisher);
            formData.append('edition', edition);
            formData.append('publicationYear', publicationYear);
            formData.append('category', category);
            formData.append('department', department);
            formData.append('description', description);
            formData.append('coverImageUrl', coverImageUrl);
            if (replacementPrice) formData.append('replacementPrice', replacementPrice);
            formData.append('initialCopies', initialCopies || '1');

            const submitBtn = addForm.querySelector('button[type="submit"]');
            if (submitBtn) submitBtn.disabled = true;

            fetch('books/add', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: formData.toString()
            })
            .then(async res => {
                const data = await res.json().catch(() => ({}));
                if (res.ok && data.success) {
                    showModalAlert(addAlert, 'success', data.message || 'Book created successfully!');
                    setTimeout(() => {
                        closeModal('addBookModal');
                        addForm.reset();
                        loadWorkspaceBooks();
                    }, 900);
                } else {
                    showModalAlert(addAlert, 'danger', data.message || 'Failed to add book.');
                    if (submitBtn) submitBtn.disabled = false;
                }
            })
            .catch(() => {
                showModalAlert(addAlert, 'danger', 'Network error connecting to library server.');
                if (submitBtn) submitBtn.disabled = false;
            });
        });
    }

    // Wire Edit Book Form
    const editForm = document.getElementById('editBookForm');
    const editAlert = document.getElementById('editBookAlert');
    if (editForm) {
        editForm.addEventListener('submit', function (e) {
            e.preventDefault();

            const bookId = document.getElementById('editBookId').value;
            const title = document.getElementById('editTitle').value.trim();
            const author = document.getElementById('editAuthor').value.trim();
            const publisher = document.getElementById('editPublisher').value.trim();
            const edition = document.getElementById('editEdition').value.trim();
            const publicationYear = document.getElementById('editYear').value.trim();
            const category = document.getElementById('editCategory').value;
            const department = document.getElementById('editDepartment').value;
            const description = document.getElementById('editDescription').value.trim();
            const coverImageUrl = document.getElementById('editCoverUrl').value.trim();

            const formData = new URLSearchParams();
            formData.append('bookId', bookId);
            formData.append('title', title);
            formData.append('author', author);
            formData.append('publisher', publisher);
            formData.append('edition', edition);
            formData.append('publicationYear', publicationYear);
            formData.append('category', category);
            formData.append('department', department);
            formData.append('description', description);
            formData.append('coverImageUrl', coverImageUrl);

            fetch('books/update', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: formData.toString()
            })
            .then(async res => {
                const data = await res.json().catch(() => ({}));
                if (res.ok && data.success) {
                    showModalAlert(editAlert, 'success', data.message || 'Book updated successfully!');
                    setTimeout(() => {
                        closeModal('editBookModal');
                        if (document.getElementById('manageBooksTableBody')) loadWorkspaceBooks();
                        if (document.getElementById('bookDetailsContainer')) location.reload();
                    }, 800);
                } else {
                    showModalAlert(editAlert, 'danger', data.message || 'Failed to update book.');
                }
            })
            .catch(() => showModalAlert(editAlert, 'danger', 'Network error updating book.'));
        });
    }

    // Wire Add Copies Form
    const addCopiesForm = document.getElementById('addCopiesForm');
    const addCopiesAlert = document.getElementById('addCopiesAlert');
    if (addCopiesForm) {
        addCopiesForm.addEventListener('submit', function (e) {
            e.preventDefault();

            const bookId = document.getElementById('addCopiesBookId').value;
            const numberOfCopies = document.getElementById('addCopiesCount').value.trim();

            const formData = new URLSearchParams();
            formData.append('bookId', bookId);
            formData.append('numberOfCopies', numberOfCopies || '1');

            fetch('books/copies/add', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: formData.toString()
            })
            .then(async res => {
                const data = await res.json().catch(() => ({}));
                if (res.ok && data.success) {
                    showModalAlert(addCopiesAlert, 'success', data.message || 'Copies added successfully!');
                    setTimeout(() => {
                        closeModal('addCopiesModal');
                        if (document.getElementById('manageBooksTableBody')) loadWorkspaceBooks();
                        if (document.getElementById('bookDetailsContainer')) location.reload();
                    }, 800);
                } else {
                    showModalAlert(addCopiesAlert, 'danger', data.message || 'Failed to add copies.');
                }
            })
            .catch(() => showModalAlert(addCopiesAlert, 'danger', 'Network error adding copies.'));
        });
    }
}

function showModalAlert(box, type, message) {
    if (!box) return;
    box.className = `alert alert-${type}`;
    box.textContent = message;
    box.style.display = 'block';
}

window.closeModal = function (modalId) {
    const modal = document.getElementById(modalId);
    if (modal) modal.classList.remove('show');
};

/**
 * Feature 10 (Module 10.6): Similar books loader for book-details.html
 */
function loadSimilarBooks(bookId) {
    const section = document.getElementById('similarBooksSection');
    const container = document.getElementById('similarBooksContainer');
    if (!section || !container) return;

    fetch(`books/similar?id=${encodeURIComponent(bookId)}&limit=6`, {
        headers: { 'Accept': 'application/json' }
    })
    .then(res => res.json())
    .then(data => {
        if (data.success && data.books && data.books.length > 0) {
            container.innerHTML = '';
            data.books.forEach(b => {
                const card = document.createElement('div');
                card.className = 'book-card';
                card.style.minWidth = '220px';

                const coverCont = document.createElement('div');
                coverCont.className = 'book-cover-container';
                const mock = document.createElement('div');
                mock.className = 'book-cover-mock mock-cover-cs';
                const dept = document.createElement('span');
                dept.className = 'mock-dept';
                dept.textContent = b.department || b.category || 'PSG iTech';
                const title = document.createElement('span');
                title.className = 'mock-title';
                title.textContent = b.title;
                const author = document.createElement('span');
                author.className = 'mock-author';
                author.textContent = b.author;
                mock.appendChild(dept);
                mock.appendChild(title);
                mock.appendChild(author);
                coverCont.appendChild(mock);
                card.appendChild(coverCont);

                const info = document.createElement('div');
                info.className = 'book-info';
                const cardTitle = document.createElement('div');
                cardTitle.className = 'book-title';
                cardTitle.textContent = b.title;
                const cardAuthor = document.createElement('div');
                cardAuthor.className = 'author-name';
                cardAuthor.textContent = b.author;
                const badgeDiv = document.createElement('div');
                badgeDiv.className = 'book-meta';
                const badge = document.createElement('span');
                const avail = b.availability;
                if (avail && avail.availableCopies > 0) {
                    badge.className = 'badge badge-available';
                    badge.textContent = `Available (${avail.availableCopies})`;
                } else {
                    badge.className = 'badge badge-borrowed';
                    badge.textContent = 'All Borrowed';
                }
                badgeDiv.appendChild(badge);

                const viewBtn = document.createElement('a');
                viewBtn.href = `book-details.html?id=${encodeURIComponent(b.bookId)}`;
                viewBtn.className = 'btn btn-secondary btn-sm';
                viewBtn.style.marginTop = '8px';
                viewBtn.style.display = 'block';
                viewBtn.style.textAlign = 'center';
                viewBtn.textContent = 'View Details';

                info.appendChild(cardTitle);
                info.appendChild(cardAuthor);
                info.appendChild(badgeDiv);
                info.appendChild(viewBtn);
                card.appendChild(info);

                container.appendChild(card);
            });
            section.style.display = 'block';
        } else {
            section.style.display = 'none';
        }
    })
    .catch(() => {
        section.style.display = 'none';
    });
}
