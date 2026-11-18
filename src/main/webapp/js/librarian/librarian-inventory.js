/**
 * PSG iTech Central Library - Librarian Portal Inventory Module
 * Manages Book Catalog, Physical Copies, Shelf Locator, and Copy Retirements.
 */

import { apiFetch, showToast } from './librarian-api.js';

let currentPage = 1;
let currentPageSize = 20;

export function initInventoryView() {
    loadInventory();

    const searchInput = document.getElementById('invSearchInput');
    const deptSelect = document.getElementById('invDeptSelect');
    const catSelect = document.getElementById('invCatSelect');
    const stockSelect = document.getElementById('invStockSelect');

    const triggerSearch = () => {
        currentPage = 1;
        loadInventory();
    };

    if (searchInput) searchInput.addEventListener('input', debounce(triggerSearch, 300));
    if (deptSelect) deptSelect.addEventListener('change', triggerSearch);
    if (catSelect) catSelect.addEventListener('change', triggerSearch);
    if (stockSelect) stockSelect.addEventListener('change', triggerSearch);

    // Add Book Form
    const addBookForm = document.getElementById('addBookForm');
    if (addBookForm) {
        addBookForm.addEventListener('submit', handleAddBookSubmit);
    }

    // Shelf Location Form
    const shelfForm = document.getElementById('shelfLocationForm');
    if (shelfForm) {
        shelfForm.addEventListener('submit', handleShelfFormSubmit);
    }
}

export async function loadInventory() {
    const q = document.getElementById('invSearchInput')?.value || '';
    const dept = document.getElementById('invDeptSelect')?.value || '';
    const cat = document.getElementById('invCatSelect')?.value || '';
    const stock = document.getElementById('invStockSelect')?.value || '';

    const tbody = document.getElementById('inventoryTableBody');
    if (tbody) tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px;">Loading catalog inventory...</td></tr>';

    try {
        const params = new URLSearchParams({
            page: currentPage,
            pageSize: currentPageSize,
            q: q.trim(),
            department: dept,
            category: cat,
            stock: stock
        });

        const res = await apiFetch(`/api/librarian/inventory?${params.toString()}`);
        if (res && res.success && res.data) {
            renderInventoryTable(res.data, res.meta);
        }
    } catch (err) {
        if (tbody) tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #B91C1C; padding: 24px;">Failed to load inventory: ${escapeHtml(err.message)}</td></tr>`;
    }
}

function renderInventoryTable(books, meta) {
    const tbody = document.getElementById('inventoryTableBody');
    if (!tbody) return;

    if (!books || books.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px; color: #5F6368;">No books match your search filters.</td></tr>';
        renderPagination(0, 0, 0);
        return;
    }

    tbody.innerHTML = books.map(b => {
        const inv = b.inventory || {};
        const avail = inv.availableCopies || 0;
        const total = inv.totalActiveCopies || 0;

        let badgeClass = 'stock-green';
        let badgeText = `${avail} / ${total} Available`;
        if (avail === 0) {
            badgeClass = 'stock-red';
            badgeText = 'Out of Stock';
        } else if (avail <= 2) {
            badgeClass = 'stock-amber';
            badgeText = `${avail} Low Stock`;
        }

        return `
            <tr>
                <td><strong>${b.bookId}</strong></td>
                <td>
                    <div style="font-weight: 600; color: #203B60;">${escapeHtml(b.title)}</div>
                    <div style="font-size: 11px; color: #5F6368;">ISBN: ${escapeHtml(b.isbn)} • Ed: ${escapeHtml(b.edition || '1st')}</div>
                </td>
                <td>${escapeHtml(b.author)}</td>
                <td>${escapeHtml(b.category || '')}</td>
                <td><span style="font-size: 11px; font-weight: 600; background: #EFECE4; padding: 2px 6px; border-radius: 3px;">${escapeHtml(b.department || 'GENERAL')}</span></td>
                <td><span class="stock-pill ${badgeClass}">${badgeText}</span></td>
                <td style="white-space: nowrap;">
                    <button class="btn-quick" style="padding: 4px 8px; font-size: 11px;" onclick="window.librarianInventory.viewBookDetails(${b.bookId})">Details</button>
                    <button class="btn-quick" style="padding: 4px 8px; font-size: 11px;" onclick="window.librarianInventory.openShelfModal(${b.bookId}, '${escapeHtml(b.title)}')">Shelf</button>
                    <button class="btn-quick" style="padding: 4px 8px; font-size: 11px;" onclick="window.librarianInventory.openAddCopiesModal(${b.bookId}, '${escapeHtml(b.title)}')">+ Copies</button>
                </td>
            </tr>
        `;
    }).join('');

    if (meta) {
        renderPagination(meta.page, meta.pageSize, meta.total);
    }
}

function renderPagination(page, pageSize, total) {
    const info = document.getElementById('invPaginationInfo');
    const prevBtn = document.getElementById('invPrevBtn');
    const nextBtn = document.getElementById('invNextBtn');

    if (info) {
        const start = total === 0 ? 0 : (page - 1) * pageSize + 1;
        const end = Math.min(page * pageSize, total);
        info.innerText = `Showing ${start}-${end} of ${total} titles`;
    }

    if (prevBtn) {
        prevBtn.disabled = page <= 1;
        prevBtn.onclick = () => { if (currentPage > 1) { currentPage--; loadInventory(); } };
    }
    if (nextBtn) {
        const totalPages = Math.ceil(total / pageSize);
        nextBtn.disabled = page >= totalPages;
        nextBtn.onclick = () => { if (currentPage < totalPages) { currentPage++; loadInventory(); } };
    }
}

export async function viewBookDetails(bookId) {
    try {
        const res = await apiFetch(`/api/librarian/inventory/details?bookId=${bookId}`);
        if (res && res.success && res.data) {
            const b = res.data.book;
            const loc = res.data.shelfLocation;

            document.getElementById('detailBookTitle').innerText = b.title;
            document.getElementById('detailAuthor').innerText = b.author;
            document.getElementById('detailIsbn').innerText = b.isbn;
            document.getElementById('detailCategory').innerText = b.category;
            document.getElementById('detailDepartment').innerText = b.department;
            document.getElementById('detailLocation').innerText = loc ? loc.fullLocationString : 'Not assigned';

            const copyList = document.getElementById('detailCopiesList');
            if (copyList) {
                const copies = b.copies || [];
                copyList.innerHTML = copies.map(c => `
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 6px 0; border-bottom: 1px solid #E5E7EB;">
                        <div>
                            <strong>${c.copyCode}</strong> 
                            <span style="font-size: 11px; margin-left: 8px; padding: 2px 6px; border-radius: 3px; background: #EFECE4;">${c.status}</span>
                        </div>
                        ${c.status === 'AVAILABLE' ? `
                            <button class="btn-quick" style="padding: 2px 8px; font-size: 11px; color: #B91C1C; border-color: #B91C1C;" 
                                onclick="window.librarianInventory.retireCopy(${c.copyId}, '${c.copyCode}')">Retire</button>
                        ` : ''}
                    </div>
                `).join('') || '<div style="color: #5F6368;">No physical copies found.</div>';
            }

            openModal('bookDetailModal');
        }
    } catch (err) {
        showToast('Failed to load book details: ' + err.message, 'error');
    }
}

export function openShelfModal(bookId, title) {
    document.getElementById('shelfBookId').value = bookId;
    document.getElementById('shelfModalTitle').innerText = 'Shelf Location: ' + title;

    // Load existing
    apiFetch(`/api/librarian/inventory/shelf-location?bookId=${bookId}`).then(res => {
        if (res && res.data) {
            const loc = res.data;
            document.getElementById('shelfFloor').value = loc.floor || 'Ground Floor';
            document.getElementById('shelfBlock').value = loc.block || 'Main Block';
            document.getElementById('shelfSection').value = loc.section || '';
            document.getElementById('shelfRack').value = loc.rack || 'R-01';
            document.getElementById('shelfShelf').value = loc.shelf || 'S-1';
        }
    }).catch(() => {});

    openModal('shelfModal');
}

export async function handleShelfFormSubmit(e) {
    e.preventDefault();
    const bookId = parseInt(document.getElementById('shelfBookId').value);
    const floor = document.getElementById('shelfFloor').value;
    const block = document.getElementById('shelfBlock').value;
    const section = document.getElementById('shelfSection').value;
    const rack = document.getElementById('shelfRack').value;
    const shelf = document.getElementById('shelfShelf').value;

    try {
        await apiFetch('/api/librarian/inventory/shelf-location', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ bookId, floor, block, section, rack, shelf })
        });
        showToast('Shelf location updated successfully!', 'success');
        closeModal('shelfModal');
        loadInventory();
    } catch (err) {
        showToast('Failed to update shelf location: ' + err.message, 'error');
    }
}

export function openAddCopiesModal(bookId, title) {
    const countStr = prompt(`Add how many physical copies for "${title}"? (1-50):`, "1");
    if (!countStr) return;
    const count = parseInt(countStr.trim());
    if (isNaN(count) || count < 1 || count > 50) {
        alert('Invalid copy count. Enter a number between 1 and 50.');
        return;
    }

    apiFetch('/api/librarian/inventory/copies', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ bookId, count })
    }).then(() => {
        showToast(`Successfully added ${count} new copies!`, 'success');
        loadInventory();
    }).catch(err => {
        showToast('Error adding copies: ' + err.message, 'error');
    });
}

export async function retireCopy(copyId, copyCode) {
    const reason = prompt(`Reason for retiring physical copy ${copyCode}:`, "Damaged beyond repair / obsolete edition");
    if (!reason) return;

    try {
        await apiFetch('/api/librarian/inventory/retire', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ copyId, reason })
        });
        showToast(`Copy ${copyCode} retired successfully.`, 'success');
        closeModal('bookDetailModal');
        loadInventory();
    } catch (err) {
        showToast('Failed to retire copy: ' + err.message, 'error');
    }
}

export async function handleAddBookSubmit(e) {
    e.preventDefault();
    const form = e.target;
    const data = {
        title: form.title.value.trim(),
        author: form.author.value.trim(),
        isbn: form.isbn.value.trim(),
        publisher: form.publisher.value.trim(),
        category: form.category.value.trim(),
        department: form.department.value,
        edition: form.edition.value.trim() || '1st',
        publicationYear: parseInt(form.publicationYear.value) || 2024,
        description: form.description.value.trim(),
        copies: parseInt(form.copies.value) || 1,
        shelfLocation: {
            floor: form.shelfFloor.value || 'Ground Floor',
            block: form.shelfBlock.value || 'Main Block',
            section: form.shelfSection.value || form.department.value,
            rack: form.shelfRack.value || 'R-01',
            shelf: form.shelfShelf.value || 'S-1'
        }
    };

    try {
        await apiFetch('/api/librarian/inventory/book', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        showToast('Book and copies added successfully to inventory!', 'success');
        closeModal('addBookModal');
        form.reset();
        loadInventory();
    } catch (err) {
        showToast('Failed to add book: ' + err.message, 'error');
    }
}

function openModal(id) {
    const m = document.getElementById(id);
    if (m) m.classList.add('show');
}

function closeModal(id) {
    const m = document.getElementById(id);
    if (m) m.classList.remove('show');
}

function debounce(fn, ms) {
    let timer;
    return function (...args) {
        clearTimeout(timer);
        timer = setTimeout(() => fn.apply(this, args), ms);
    };
}

function escapeHtml(text) {
    if (!text) return '';
    return text.replace(/[&<>"']/g, m => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[m]));
}

// Expose handlers globally for onclick attributes
window.librarianInventory = {
    viewBookDetails,
    openShelfModal,
    openAddCopiesModal,
    retireCopy
};
