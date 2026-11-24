/**
 * PSG iTech Central Library - Master Librarian Portal Orchestrator
 * Coordinates Hash Routing, Dropdowns, Global Search, and Module Initialization.
 */

import { CTX, apiFetch, showToast } from './librarian-api.js';
import { initHomeView, stopStatsPolling, startStatsPolling } from './librarian-home.js';
import { initInventoryView } from './librarian-inventory.js';
import { initCirculationView } from './librarian-circulation.js';
import { initStudentsView } from './librarian-students.js';
import { initQueuesView } from './librarian-queues.js';
import { initFinesView } from './librarian-fines.js';
import { initOperationsModule, loadAcquisitions, loadStudentQueries, loadPurchases, loadFloorHeatmap, loadHighDemandAlerts } from './librarian-operations.js';
import { initReportsModule, loadActivityLogs, generateCurrentReport } from './librarian-reports.js';

let initializedViews = new Set();

document.addEventListener('DOMContentLoaded', () => {
    setupNavigationRouting();
    setupDropdowns();
    setupGlobalSearch();
    setupNotificationBell();
    setupMobileMenu();

    initOperationsModule();
    initReportsModule();

    // Trigger initial route
    handleHashChange();
    window.addEventListener('hashchange', handleHashChange);
});

function setupNavigationRouting() {
    document.querySelectorAll('.nav-pill').forEach(link => {
        link.addEventListener('click', (e) => {
            const href = link.getAttribute('href');
            if (href && href.startsWith('#')) {
                window.location.hash = href;
            }
        });
    });
}

function handleHashChange() {
    let hash = window.location.hash || '#home';
    let cleanHash = hash.split('?')[0];

    // Handle due calendar redirect to circulation sub-tab
    if (cleanHash === '#operations/due-calendar') {
        window.location.hash = '#circulation';
        if (window.librarianCirculation && window.librarianCirculation.loadCirculationTab) {
            document.querySelectorAll('.circ-tab-btn').forEach(b => {
                if (b.dataset.tab === 'due-calendar') b.classList.add('active');
                else b.classList.remove('active');
            });
            window.librarianCirculation.loadCirculationTab('due-calendar');
        }
        return;
    }

    let primaryTab = cleanHash.split('/')[0];
    let subRoute = cleanHash.substring(1).replace(/\//g, '-');

    // Update active nav pill
    document.querySelectorAll('.nav-pill').forEach(pill => {
        const target = pill.getAttribute('href');
        if (target === primaryTab || (primaryTab === '#home' && target === '#home')) {
            pill.classList.add('active');
        } else {
            pill.classList.remove('active');
        }
    });

    // Hide all view sections
    document.querySelectorAll('.view-section').forEach(sec => sec.classList.remove('active'));

    let sectionId = 'section-' + subRoute;
    let targetSection = document.getElementById(sectionId);
    if (!targetSection) {
        sectionId = 'section-' + primaryTab.substring(1);
        targetSection = document.getElementById(sectionId);
    }

    if (targetSection) {
        targetSection.classList.add('active');
    } else {
        const homeSec = document.getElementById('section-home');
        if (homeSec) homeSec.classList.add('active');
        primaryTab = '#home';
    }

    // Initialize/load modules on route activation
    if (primaryTab === '#home') {
        if (!initializedViews.has('home')) {
            initHomeView();
            initializedViews.add('home');
        } else {
            startStatsPolling();
        }
    } else {
        stopStatsPolling();
    }

    if (primaryTab === '#inventory' && !initializedViews.has('inventory')) {
        initInventoryView();
        initializedViews.add('inventory');
    } else if (primaryTab === '#circulation' && !initializedViews.has('circulation')) {
        initCirculationView();
        initializedViews.add('circulation');
    } else if (primaryTab === '#students' && !initializedViews.has('students')) {
        initStudentsView();
        initializedViews.add('students');
    } else if (primaryTab === '#queues' && !initializedViews.has('queues')) {
        initQueuesView();
        initializedViews.add('queues');
    } else if (primaryTab === '#fines' && !initializedViews.has('fines')) {
        initFinesView();
        initializedViews.add('fines');
    } else if (cleanHash === '#operations/acquisitions') {
        loadAcquisitions();
    } else if (cleanHash === '#operations/queries') {
        loadStudentQueries();
    } else if (cleanHash === '#operations/purchases') {
        loadPurchases();
    } else if (cleanHash === '#operations/shelf-locator') {
        loadFloorHeatmap();
    } else if (cleanHash === '#operations/high-demand') {
        loadHighDemandAlerts();
    } else if (cleanHash === '#reports/activity-logs') {
        loadActivityLogs(1);
    } else if (cleanHash === '#reports/analytics') {
        generateCurrentReport();
    }
}

function setupDropdowns() {
    document.querySelectorAll('.nav-dropdown-trigger').forEach(trigger => {
        trigger.addEventListener('click', (e) => {
            e.stopPropagation();
            const popover = trigger.nextElementSibling;
            const isOpen = popover.classList.contains('show');

            // Close other popovers
            document.querySelectorAll('.dropdown-popover').forEach(p => p.classList.remove('show'));
            document.querySelectorAll('.nav-dropdown-trigger').forEach(t => t.setAttribute('aria-expanded', 'false'));

            if (!isOpen) {
                popover.classList.add('show');
                trigger.setAttribute('aria-expanded', 'true');
            }
        });
    });

    // Profile dropdown
    const profileBtn = document.getElementById('profilePillBtn');
    const profileMenu = document.getElementById('profilePopoverMenu');
    if (profileBtn && profileMenu) {
        profileBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            profileMenu.classList.toggle('show');
        });
    }

    const switchBtn = document.getElementById('btnSwitchToStudent');
    if (switchBtn) {
        switchBtn.addEventListener('click', (e) => {
            window.switchToStudentPortal(e);
        });
    }

    // Close when clicking outside
    document.addEventListener('click', () => {
        document.querySelectorAll('.dropdown-popover').forEach(p => p.classList.remove('show'));
        document.querySelectorAll('.nav-dropdown-trigger').forEach(t => t.setAttribute('aria-expanded', 'false'));
        if (profileMenu) profileMenu.classList.remove('show');
        const notifMenu = document.getElementById('notificationPopover');
        if (notifMenu) notifMenu.style.display = 'none';
        const searchDropdown = document.getElementById('globalSearchResults');
        if (searchDropdown) searchDropdown.style.display = 'none';
    });

    // ESC closes dropdowns
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            document.querySelectorAll('.dropdown-popover').forEach(p => p.classList.remove('show'));
            if (profileMenu) profileMenu.classList.remove('show');
            const searchDropdown = document.getElementById('globalSearchResults');
            if (searchDropdown) searchDropdown.style.display = 'none';
        }
    });
}

function setupGlobalSearch() {
    const input = document.getElementById('globalSearchInput');
    const dropdown = document.getElementById('globalSearchResults');
    if (!input || !dropdown) return;

    let debounceTimer = null;
    let inFlightAbort = null;

    input.addEventListener('input', () => {
        clearTimeout(debounceTimer);
        const query = input.value.trim();

        if (query.length < 2) {
            dropdown.style.display = 'none';
            return;
        }

        debounceTimer = setTimeout(async () => {
            if (inFlightAbort) inFlightAbort.abort();
            inFlightAbort = new AbortController();

            try {
                const res = await apiFetch(`/api/librarian/search?q=${encodeURIComponent(query)}`, {
                    signal: inFlightAbort.signal
                });

                if (res && res.success && res.data) {
                    renderSearchResults(res.data, dropdown);
                }
            } catch (err) {
                if (err.name !== 'AbortError') {
                    console.error('Search error:', err);
                }
            }
        }, 300);
    });

    // Click inside input keeps dropdown open
    input.addEventListener('click', (e) => e.stopPropagation());
    dropdown.addEventListener('click', (e) => e.stopPropagation());
}

function renderSearchResults(data, container) {
    const books = data.books || [];
    const students = data.students || [];
    const transactions = data.transactions || [];

    if (books.length === 0 && students.length === 0 && transactions.length === 0) {
        container.innerHTML = '<div style="padding: 16px; text-align: center; color: #5F6368; font-size: 13px;">No matching results found.</div>';
        container.style.display = 'block';
        return;
    }

    let html = '';

    if (books.length > 0) {
        html += '<div class="search-group-title">Books & Copies</div>';
        html += books.map(b => `
            <a href="#inventory" class="search-result-item" onclick="window.librarianInventory.viewBookDetails(${b.bookId})">
                <div style="font-weight: 600; color: #203B60;">${escapeHtml(b.title)}</div>
                <div style="font-size: 11px; color: #5F6368;">${escapeHtml(b.author)} • ISBN: ${escapeHtml(b.isbn)} ${b.copyCode ? '• Copy: ' + b.copyCode : ''}</div>
            </a>
        `).join('');
    }

    if (students.length > 0) {
        html += '<div class="search-group-title">Students</div>';
        html += students.map(s => `
            <a href="#students" class="search-result-item" onclick="window.librarianStudents.viewStudentProfile(${s.userId})">
                <div style="font-weight: 600; color: #203B60;">${escapeHtml(s.name)} (${escapeHtml(s.registerNumber || '--')})</div>
                <div style="font-size: 11px; color: #5F6368;">${escapeHtml(s.department || '--')} • ${escapeHtml(s.email)}</div>
            </a>
        `).join('');
    }

    if (transactions.length > 0) {
        html += '<div class="search-group-title">Borrowing Transactions</div>';
        html += transactions.map(t => `
            <a href="#circulation" class="search-result-item">
                <div style="font-weight: 600; color: #203B60;">Tx #${t.transactionId}: ${escapeHtml(t.title)}</div>
                <div style="font-size: 11px; color: #5F6368;">Holder: ${escapeHtml(t.studentName)} (${escapeHtml(t.registerNumber || '')}) • Status: ${t.status}</div>
            </a>
        `).join('');
    }

    container.innerHTML = html;
    container.style.display = 'block';
}

function setupNotificationBell() {
    const bellBtn = document.getElementById('notifBellBtn');
    const popover = document.getElementById('notificationPopover');
    if (!bellBtn || !popover) return;

    bellBtn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const isOpen = popover.style.display === 'block';
        popover.style.display = isOpen ? 'none' : 'block';

        if (!isOpen) {
            try {
                const res = await apiFetch('/notifications?limit=10');
                if (res && res.success && res.data) {
                    renderNotificationList(res.data, popover);
                }
            } catch (err) {
                popover.innerHTML = `<div style="padding: 16px; color: #B91C1C;">Failed to load notifications.</div>`;
            }
        }
    });

    popover.addEventListener('click', (e) => e.stopPropagation());
}

function renderNotificationList(notifications, container) {
    if (!notifications || notifications.length === 0) {
        container.innerHTML = '<div style="padding: 20px; text-align: center; color: #5F6368; font-size: 13px;">No notifications.</div>';
        return;
    }

    container.innerHTML = `
        <div style="padding: 10px 14px; border-bottom: 1px solid #E2DDD5; font-weight: 600; font-size: 13px; color: #203B60;">
            Staff Alerts & Notifications
        </div>
        <div style="max-height: 360px; overflow-y: auto;">
            ${notifications.map(n => `
                <div style="padding: 10px 14px; border-bottom: 1px solid #F3F4F6; font-size: 12px; ${n.isRead ? '' : 'background: #F8FAFC;'}">
                    <div style="font-weight: 600; color: #203B60; margin-bottom: 2px;">${escapeHtml(n.title)}</div>
                    <div style="color: #444444; margin-bottom: 4px;">${escapeHtml(n.message)}</div>
                    <div style="color: #5F6368; font-size: 10px;">${n.createdAt || ''}</div>
                </div>
            `).join('')}
        </div>
    `;
}

function setupMobileMenu() {
    const burgerBtn = document.getElementById('hamburgerBtn');
    const centerCluster = document.querySelector('.nav-cluster-center');
    if (burgerBtn && centerCluster) {
        burgerBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            centerCluster.classList.toggle('mobile-open');
        });

        // Close mobile menu when clicking outside
        document.addEventListener('click', (e) => {
            if (!centerCluster.contains(e.target) && !burgerBtn.contains(e.target)) {
                centerCluster.classList.remove('mobile-open');
            }
        });

        // Close mobile menu when a navigation link is clicked
        centerCluster.addEventListener('click', (e) => {
            if (e.target.tagName === 'A' || e.target.closest('a')) {
                centerCluster.classList.remove('mobile-open');
            }
        });
    }
}

function escapeHtml(text) {
    if (!text) return '';
    return text.replace(/[&<>"']/g, m => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[m]));
}

window.switchToStudentPortal = function(e) {
    if (e && e.preventDefault) e.preventDefault();
    sessionStorage.removeItem('psgitech_user');
    sessionStorage.removeItem('psgitech_csrf_token');
    localStorage.removeItem('psgitech_user');
    localStorage.removeItem('psgitech_remember_me');
    const ctx = (typeof CTX !== 'undefined' ? CTX : '');
    fetch(ctx + '/logout', { method: 'POST' }).finally(() => {
        window.location.href = ctx + '/login.html';
    });
};
