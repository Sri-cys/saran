/**
 * PSG iTech Central Library - Librarian Portal Home Module
 * Implements Section 4 & Section 7 (Real-Time 12s Polling, KPIs, Department Analytics, Collections)
 */

import { apiFetch, showToast } from './librarian-api.js';

let pollingInterval = null;
let pollingBackoffMs = 12000;
let isFetching = false;
let currentAbortController = null;
let currentWindowDays = 30;

let chart1Instance = null;
let chart2Instance = null;

export function initHomeView() {
    loadDashboardStats();
    loadDepartmentCharts(currentWindowDays);
    loadHomeCollections();
    startStatsPolling();

    // Setup window filter buttons
    document.querySelectorAll('.window-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            document.querySelectorAll('.window-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            const w = btn.dataset.window;
            currentWindowDays = (w === 'all') ? 0 : parseInt(w);
            loadDepartmentCharts(currentWindowDays);
        });
    });

    // Visibility change listener: pause polling when tab is hidden, refresh immediately on focus
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
            stopStatsPolling();
        } else {
            loadDashboardStats();
            startStatsPolling();
        }
    });
}

export function startStatsPolling() {
    stopStatsPolling();
    pollingInterval = setInterval(async () => {
        if (document.hidden || isFetching) return;
        await loadDashboardStats();
    }, pollingBackoffMs);
}

export function stopStatsPolling() {
    if (pollingInterval) {
        clearInterval(pollingInterval);
        pollingInterval = null;
    }
}

export async function loadDashboardStats() {
    if (isFetching) return;
    isFetching = true;

    try {
        currentAbortController = new AbortController();
        const res = await apiFetch('/api/librarian/dashboard/stats', { signal: currentAbortController.signal });
        if (res && res.success && res.data) {
            renderKpis(res.data);
            pollingBackoffMs = 12000; // reset backoff on success
        }
    } catch (err) {
        if (err.name !== 'AbortError') {
            console.warn('Dashboard stats polling failed:', err.message);
            // Exponential backoff capped at 60s
            pollingBackoffMs = Math.min(60000, pollingBackoffMs * 1.5);
            startStatsPolling();
        }
    } finally {
        isFetching = false;
    }
}

function renderKpis(data) {
    document.getElementById('kpiTotalCopies').innerText = data.totalCopies || 0;
    document.getElementById('kpiAvailable').innerText = data.availableCopies || 0;
    document.getElementById('kpiBorrowed').innerText = data.borrowedCopies || 0;
    document.getElementById('kpiOverdue').innerText = data.overdueCopies || 0;
    document.getElementById('kpiPendingReservations').innerText = data.pendingReservations || 0;

    const banner = document.getElementById('inconsistencyBanner');
    if (data.invariantSatisfied === false && data.inconsistencyNotice) {
        banner.innerText = data.inconsistencyNotice;
        banner.style.display = 'flex';
    } else {
        banner.style.display = 'none';
    }

    const lastUpdatedEl = document.getElementById('homeLastUpdated');
    if (lastUpdatedEl && data.lastUpdated) {
        lastUpdatedEl.innerText = data.lastUpdated;
    }
}

export async function loadDepartmentCharts(windowDays) {
    try {
        const res = await apiFetch(`/api/librarian/dashboard/charts?window=${windowDays}`);
        if (res && res.success && res.data) {
            renderDepartmentCharts(res.data);
        }
    } catch (err) {
        console.error('Failed to load department charts:', err);
    }
}

function renderDepartmentCharts(data) {
    const bookDepts = data.bookDepartmentStats || [];
    const studentDepts = data.studentDepartmentStats || [];

    // Fallback tables
    renderTableFallback('chart1TableBody', bookDepts, (item) => `
        <tr><td>${escapeHtml(item.department)}</td><td>${item.borrowCount}</td></tr>
    `);

    renderTableFallback('chart2TableBody', studentDepts, (item) => `
        <tr><td>${escapeHtml(item.department)}</td><td>${item.uniqueStudents}</td><td>${item.totalBooksTaken}</td></tr>
    `);

    // Render using Chart.js if loaded via CDN
    if (typeof Chart !== 'undefined') {
        const ctx1 = document.getElementById('chartBookDept')?.getContext('2d');
        if (ctx1) {
            if (chart1Instance) chart1Instance.destroy();
            chart1Instance = new Chart(ctx1, {
                type: 'bar',
                data: {
                    labels: bookDepts.map(d => d.department),
                    datasets: [{
                        label: 'Loans Taken',
                        data: bookDepts.map(d => d.borrowCount),
                        backgroundColor: '#203B60'
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: { legend: { display: false } },
                    scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
                }
            });
        }

        const ctx2 = document.getElementById('chartStudentDept')?.getContext('2d');
        if (ctx2) {
            if (chart2Instance) chart2Instance.destroy();
            chart2Instance = new Chart(ctx2, {
                type: 'bar',
                data: {
                    labels: studentDepts.map(d => d.department),
                    datasets: [
                        { label: 'Unique Students', data: studentDepts.map(d => d.uniqueStudents), backgroundColor: '#159B50' },
                        { label: 'Total Books Borrowed', data: studentDepts.map(d => d.totalBooksTaken), backgroundColor: '#203B60' }
                    ]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
                }
            });
        }
    }
}

function renderTableFallback(tbodyId, list, rowHtmlFn) {
    const tbody = document.getElementById(tbodyId);
    if (!tbody) return;
    if (!list || list.length === 0) {
        tbody.innerHTML = '<tr><td colspan="3" style="text-align: center; color: #5F6368;">No borrowing records in this period.</td></tr>';
        return;
    }
    tbody.innerHTML = list.map(rowHtmlFn).join('');
}

export async function loadHomeCollections() {
    try {
        const res = await apiFetch('/api/librarian/dashboard/collections');
        if (res && res.success && res.data) {
            renderCategoryTrack(res.data.categories || []);
            renderVerticalCollection('recentBooksContainer', res.data.recentlyAdded || []);
            renderVerticalCollection('popularBooksContainer', res.data.mostBorrowed || []);
            renderVerticalCollection('highDemandBooksContainer', res.data.highDemand || []);
        }
    } catch (err) {
        console.error('Failed to load collections:', err);
    }
}

function renderCategoryTrack(categories) {
    const track = document.getElementById('categoryScrollerTrack');
    if (!track) return;
    if (categories.length === 0) {
        track.innerHTML = '<div style="color: #5F6368; padding: 12px;">No categories found.</div>';
        return;
    }

    track.innerHTML = categories.map(c => `
        <a href="#inventory" class="category-chip-card" data-category="${escapeHtml(c.category)}">
            <div class="category-chip-title">${escapeHtml(c.category)}</div>
            <div class="category-chip-count">${c.bookCount} Titles • ${c.copyCount} Copies</div>
        </a>
    `).join('');

    track.querySelectorAll('.category-chip-card').forEach(card => {
        card.addEventListener('click', () => {
            window.location.hash = `#inventory?category=${encodeURIComponent(card.dataset.category)}`;
        });
    });
}

function renderVerticalCollection(containerId, books) {
    const container = document.getElementById(containerId);
    if (!container) return;
    if (!books || books.length === 0) {
        container.innerHTML = '<div style="color: #5F6368; padding: 16px; text-align: center;">No titles in this collection.</div>';
        return;
    }

    container.innerHTML = books.map(b => {
        const avail = b.availableCopies || 0;
        let pillClass = 'stock-green';
        let pillText = `Available (${avail} copies)`;
        if (avail === 0) {
            pillClass = 'stock-red';
            pillText = 'Out of Stock';
        } else if (avail <= 2) {
            pillClass = 'stock-amber';
            pillText = `Low Stock (${avail} copies)`;
        }

        return `
            <div class="vertical-book-card">
                <div class="book-cover-thumb">
                    <span class="thumb-title">${escapeHtml(b.title ? b.title.substring(0, 22) : 'PSG')}</span>
                </div>
                <div class="book-card-meta">
                    <div class="book-card-title" title="${escapeHtml(b.title)}">${escapeHtml(b.title)}</div>
                    <div class="book-card-author">${escapeHtml(b.author)} • ${escapeHtml(b.category || '')}</div>
                    <div style="margin-top: 4px;">
                        <span class="stock-pill ${pillClass}">${pillText}</span>
                    </div>
                </div>
            </div>
        `;
    }).join('');
}

function escapeHtml(text) {
    if (!text) return '';
    return text.replace(/[&<>"']/g, m => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[m]));
}
