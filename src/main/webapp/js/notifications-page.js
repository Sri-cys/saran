/**
 * PSG iTech Library - Feature 11: Notification Center Page Scripts
 * Handles tab filtering, keyset pagination, marking items as read,
 * and institutional channel preference management.
 */
(function () {
    'use strict';

    const CONTEXT_PATH = (function () {
        const p = window.location.pathname;
        if (p.startsWith('/psgitech-library')) return '/psgitech-library';
        return '';
    })();

    let currentTab = 'ALL';
    let currentCursor = null;
    let hasMore = false;
    let isLoading = false;

    function getCsrfToken() {
        const meta = document.querySelector('meta[name="csrf-token"]');
        if (meta) return meta.getAttribute('content');
        const match = document.cookie.match(/csrfToken=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : '';
    }

    document.addEventListener('DOMContentLoaded', function () {
        setupTabs();
        setupActions();
        loadNotifications(true);
    });

    function setupTabs() {
        const tabButtons = document.querySelectorAll('.notif-tab-btn');
        tabButtons.forEach(btn => {
            btn.addEventListener('click', function () {
                const targetTab = this.getAttribute('data-tab');
                if (targetTab === currentTab) return;

                tabButtons.forEach(b => b.classList.remove('active'));
                this.classList.add('active');
                currentTab = targetTab;

                const feedSection = document.getElementById('feed-section');
                const prefSection = document.getElementById('preferences-section');

                if (currentTab === 'PREFERENCES') {
                    feedSection.style.display = 'none';
                    prefSection.style.display = 'block';
                    loadPreferences();
                } else {
                    feedSection.style.display = 'block';
                    prefSection.style.display = 'none';
                    loadNotifications(true);
                }
            });
        });
    }

    function setupActions() {
        const markAllBtn = document.getElementById('page-mark-all-btn');
        if (markAllBtn) {
            markAllBtn.addEventListener('click', function () {
                markAllRead();
            });
        }

        const loadMoreBtn = document.getElementById('load-more-btn');
        if (loadMoreBtn) {
            loadMoreBtn.addEventListener('click', function () {
                if (hasMore && !isLoading) {
                    loadNotifications(false);
                }
            });
        }

        const prefForm = document.getElementById('preferences-form');
        if (prefForm) {
            prefForm.addEventListener('submit', function (e) {
                e.preventDefault();
                savePreferences();
            });
        }
    }

    function loadNotifications(reset) {
        if (isLoading) return;
        isLoading = true;

        const listContainer = document.getElementById('notifications-list');
        const spinner = document.getElementById('loading-spinner');
        const emptyState = document.getElementById('empty-state');
        const loadMoreContainer = document.getElementById('load-more-container');

        if (reset) {
            currentCursor = null;
            listContainer.innerHTML = '';
            emptyState.style.display = 'none';
            loadMoreContainer.style.display = 'none';
        }

        spinner.style.display = 'block';

        let url = `${CONTEXT_PATH}/notifications?limit=20`;
        if (currentTab === 'UNREAD') {
            url += '&unreadOnly=true';
        } else if (currentTab !== 'ALL' && currentTab !== 'PREFERENCES') {
            url += `&category=${encodeURIComponent(currentTab)}`;
        }

        if (currentCursor) {
            url += `&cursor=${encodeURIComponent(currentCursor)}`;
        }

        fetch(url, {
            method: 'GET',
            headers: { 'Accept': 'application/json' }
        })
            .then(res => {
                if (res.status === 401) {
                    window.location.href = `${CONTEXT_PATH}/login.html`;
                    return null;
                }
                return res.ok ? res.json() : null;
            })
            .then(data => {
                spinner.style.display = 'none';
                isLoading = false;

                if (!data || !data.notifications) {
                    if (reset) emptyState.style.display = 'block';
                    return;
                }

                hasMore = data.hasMore;
                currentCursor = data.nextCursor;

                if (reset && data.notifications.length === 0) {
                    emptyState.style.display = 'block';
                    loadMoreContainer.style.display = 'none';
                    return;
                }

                renderNotifications(data.notifications, !reset);

                if (hasMore) {
                    loadMoreContainer.style.display = 'block';
                } else {
                    loadMoreContainer.style.display = 'none';
                }
            })
            .catch(err => {
                spinner.style.display = 'none';
                isLoading = false;
                console.error("Failed to load notifications", err);
            });
    }

    function renderNotifications(items, append) {
        const listContainer = document.getElementById('notifications-list');
        if (!append) {
            listContainer.innerHTML = '';
        }

        items.forEach(item => {
            const card = document.createElement('div');
            card.className = `notif-card ${item.read ? 'read' : 'unread'}`;
            card.setAttribute('data-id', item.notificationId);

            const categoryLabel = formatCategory(item.category);
            const timeAgo = formatDateTime(item.createdAt);

            let actionHtml = '';
            if (item.actionUrl) {
                actionHtml += `<a href="${item.actionUrl}" class="notif-action-link">View Details &rarr;</a>`;
            }

            let markBtnHtml = '';
            if (!item.read) {
                markBtnHtml = `<button class="notif-mark-read-btn" type="button" data-action="mark-read">Mark as read</button>`;
            }

            card.innerHTML = `
                <div class="notif-content">
                    <div class="notif-meta">
                        <span class="notif-category-pill">${categoryLabel}</span>
                        <span>${timeAgo}</span>
                    </div>
                    <div class="notif-title">${escapeHtml(item.title)}</div>
                    <div class="notif-body">${escapeHtml(item.message)}</div>
                    <div class="notif-actions">
                        ${actionHtml}
                    </div>
                </div>
                <div>
                    ${markBtnHtml}
                </div>
            `;

            const markBtn = card.querySelector('[data-action="mark-read"]');
            if (markBtn) {
                markBtn.addEventListener('click', function (e) {
                    e.stopPropagation();
                    markSingleRead(item.notificationId, card);
                });
            }

            listContainer.appendChild(card);
        });
    }

    function markSingleRead(notificationId, cardEl) {
        fetch(`${CONTEXT_PATH}/notifications/read`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-Token': getCsrfToken()
            },
            body: JSON.stringify({ notificationId: notificationId })
        })
            .then(res => res.ok ? res.json() : null)
            .then(data => {
                if (data && data.success) {
                    cardEl.classList.remove('unread');
                    cardEl.classList.add('read');
                    const markBtn = cardEl.querySelector('[data-action="mark-read"]');
                    if (markBtn) markBtn.remove();
                    window.dispatchEvent(new CustomEvent('notifications:refresh'));
                }
            })
            .catch(console.error);
    }

    function markAllRead() {
        fetch(`${CONTEXT_PATH}/notifications/read-all`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-Token': getCsrfToken()
            },
            body: JSON.stringify({ upToId: 0 })
        })
            .then(res => res.ok ? res.json() : null)
            .then(data => {
                if (data && data.success) {
                    document.querySelectorAll('.notif-card.unread').forEach(el => {
                        el.classList.remove('unread');
                        el.classList.add('read');
                        const btn = el.querySelector('[data-action="mark-read"]');
                        if (btn) btn.remove();
                    });
                    window.dispatchEvent(new CustomEvent('notifications:refresh'));
                }
            })
            .catch(console.error);
    }

    function loadPreferences() {
        const tbody = document.getElementById('preferences-table-body');
        if (!tbody) return;

        tbody.innerHTML = '<tr><td colspan="3" style="text-align: center;">Loading preferences...</td></tr>';

        fetch(`${CONTEXT_PATH}/notifications/preferences`, {
            method: 'GET',
            headers: { 'Accept': 'application/json' }
        })
            .then(res => res.ok ? res.json() : null)
            .then(data => {
                if (!data || !data.preferences) {
                    tbody.innerHTML = '<tr><td colspan="3" style="text-align: center;">Failed to load preferences.</td></tr>';
                    return;
                }

                tbody.innerHTML = '';
                data.preferences.forEach(pref => {
                    const tr = document.createElement('tr');
                    const catName = formatCategory(pref.category);
                    const emailLocked = pref.category === 'FINES_PAYMENTS' || pref.category === 'SYSTEM';
                    const inAppLocked = pref.category !== 'DISCOVERY';

                    let emailBadge = '';
                    if (emailLocked) {
                        emailBadge = `<span class="pref-locked-badge" title="${escapeHtml(pref.lockReason || 'Mandatory institution channel')}">Mandatory</span>`;
                    }

                    tr.innerHTML = `
                        <td>
                            <strong>${catName}</strong>
                            <input type="hidden" name="category" value="${pref.category}">
                        </td>
                        <td>
                            <label class="toggle-switch">
                                <input type="checkbox" name="inApp_${pref.category}" ${pref.inAppEnabled ? 'checked' : ''} ${inAppLocked ? 'disabled' : ''}>
                                <span class="toggle-slider"></span>
                            </label>
                        </td>
                        <td>
                            <label class="toggle-switch">
                                <input type="checkbox" name="email_${pref.category}" ${pref.emailEnabled ? 'checked' : ''} ${emailLocked ? 'disabled' : ''}>
                                <span class="toggle-slider"></span>
                            </label>
                            ${emailBadge}
                        </td>
                    `;
                    tbody.appendChild(tr);
                });
            })
            .catch(() => {
                tbody.innerHTML = '<tr><td colspan="3" style="text-align: center;">Error loading preferences.</td></tr>';
            });
    }

    function savePreferences() {
        const tbody = document.getElementById('preferences-table-body');
        const rows = tbody.querySelectorAll('tr');
        const updates = [];

        rows.forEach(r => {
            const catInput = r.querySelector('input[name="category"]');
            if (!catInput) return;
            const cat = catInput.value;
            const inAppBox = r.querySelector(`input[name="inApp_${cat}"]`);
            const emailBox = r.querySelector(`input[name="email_${cat}"]`);

            updates.push({
                category: cat,
                inAppEnabled: inAppBox ? inAppBox.checked : true,
                emailEnabled: emailBox ? emailBox.checked : true
            });
        });

        const statusMsg = document.getElementById('pref-status-msg');

        fetch(`${CONTEXT_PATH}/notifications/preferences`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-Token': getCsrfToken()
            },
            body: JSON.stringify({ preferences: updates })
        })
            .then(res => res.ok ? res.json() : null)
            .then(data => {
                if (data && data.success) {
                    if (statusMsg) {
                        statusMsg.style.display = 'inline';
                        setTimeout(() => { statusMsg.style.display = 'none'; }, 3000);
                    }
                    loadPreferences();
                }
            })
            .catch(err => {
                alert("Failed to save preferences: " + err.message);
            });
    }

    function formatCategory(cat) {
        if (!cat) return 'General';
        switch (cat) {
            case 'RESERVATION': return 'Reservation';
            case 'ALLOCATION_REMINDERS': return 'Pickup Reminder';
            case 'BORROWING': return 'Borrowing';
            case 'DUE_REMINDERS': return 'Due Reminder';
            case 'FINES_PAYMENTS': return 'Fines & Payments';
            case 'INTERCHANGE': return 'Interchange';
            case 'SYSTEM': return 'System Security';
            case 'STAFF': return 'Staff';
            case 'DISCOVERY': return 'Book Discovery';
            default: return cat;
        }
    }

    function formatDateTime(str) {
        if (!str) return '';
        const d = new Date(str);
        return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }
})();
