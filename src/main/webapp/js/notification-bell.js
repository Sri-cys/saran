/**
 * PSG iTech Library - Feature 11: Notification Bell & Quick Dropdown Component
 * Automatically injects the notification bell into navigation headers,
 * manages 45-second background polling with tab visibility detection,
 * and handles quick-read interactions.
 */
(function () {
    'use strict';

    const NOTIFICATION_POLL_MS = 45000;
    const CONTEXT_PATH = (function () {
        const p = window.location.pathname;
        if (p.startsWith('/psgitech-library')) return '/psgitech-library';
        return '';
    })();

    let pollTimer = null;
    let abortController = null;
    let unreadCount = 0;
    let latestId = 0;

    function getCsrfToken() {
        const meta = document.querySelector('meta[name="csrf-token"]');
        if (meta) return meta.getAttribute('content');
        const match = document.cookie.match(/csrfToken=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : '';
    }

    function initNotificationBell() {
        const existingBell = document.querySelector('.notification-bell, #notificationBtn');
        const navContainer = document.querySelector('.nav-right, .nav-actions, .user-nav, header nav ul, .navbar-nav');
        if (!existingBell && !navContainer) {
            return;
        }

        if (document.getElementById('notification-bell-container')) {
            return; // Already initialized
        }

        const bellWrapper = document.createElement('div');
        bellWrapper.id = 'notification-bell-container';
        bellWrapper.className = 'notif-bell-wrapper';
        bellWrapper.innerHTML = `
            <button id="notif-bell-btn" class="notif-bell-btn" aria-label="Notifications" aria-expanded="false" title="Notifications">
                <svg class="notif-bell-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
                    <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
                </svg>
                <span id="notif-badge" class="notif-badge" style="display: none;">0</span>
            </button>
            <div id="notif-dropdown" class="notif-dropdown" style="display: none;" role="region" aria-label="Recent notifications">
                <div class="notif-dropdown-header">
                    <span class="notif-header-title">Notifications</span>
                    <button id="notif-mark-all-read" class="notif-header-action" type="button">Mark all read</button>
                </div>
                <div id="notif-dropdown-list" class="notif-dropdown-list">
                    <div class="notif-empty-state">Loading notifications...</div>
                </div>
                <div class="notif-dropdown-footer">
                    <a href="${CONTEXT_PATH}/notifications.html" class="notif-view-all-link">View all notifications &rarr;</a>
                </div>
            </div>
        `;

        if (existingBell) {
            existingBell.parentNode.replaceChild(bellWrapper, existingBell);
        } else if (navContainer && navContainer.firstChild) {
            navContainer.insertBefore(bellWrapper, navContainer.firstChild);
        } else if (navContainer) {
            navContainer.appendChild(bellWrapper);
        }
        }

        bindBellEvents();
        fetchUnreadCount();
        startPolling();
    }

    function bindBellEvents() {
        const bellBtn = document.getElementById('notif-bell-btn');
        const dropdown = document.getElementById('notif-dropdown');
        const markAllBtn = document.getElementById('notif-mark-all-read');

        if (!bellBtn || !dropdown) return;

        bellBtn.addEventListener('click', function (e) {
            e.stopPropagation();
            const isOpen = dropdown.style.display !== 'none';
            if (isOpen) {
                dropdown.style.display = 'none';
                bellBtn.setAttribute('aria-expanded', 'false');
            } else {
                dropdown.style.display = 'block';
                bellBtn.setAttribute('aria-expanded', 'true');
                loadRecentNotifications();
            }
        });

        if (markAllBtn) {
            markAllBtn.addEventListener('click', function (e) {
                e.stopPropagation();
                markAllRead();
            });
        }

        // Close dropdown when clicking outside
        document.addEventListener('click', function (e) {
            if (!dropdown.contains(e.target) && !bellBtn.contains(e.target)) {
                dropdown.style.display = 'none';
                bellBtn.setAttribute('aria-expanded', 'false');
            }
        });

        // Close on Escape key
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape' && dropdown.style.display !== 'none') {
                dropdown.style.display = 'none';
                bellBtn.setAttribute('aria-expanded', 'false');
                bellBtn.focus();
            }
        });

        // Visibility API: pause polling when tab is hidden
        document.addEventListener('visibilitychange', function () {
            if (document.hidden) {
                stopPolling();
            } else {
                fetchUnreadCount();
                startPolling();
            }
        });

        // External refresh trigger
        window.addEventListener('notifications:refresh', function () {
            fetchUnreadCount();
        });
    }

    function startPolling() {
        stopPolling();
        pollTimer = setInterval(function () {
            fetchUnreadCount();
        }, NOTIFICATION_POLL_MS);
    }

    function stopPolling() {
        if (pollTimer) {
            clearInterval(pollTimer);
            pollTimer = null;
        }
    }

    function updateBadge(count) {
        unreadCount = count;
        const badge = document.getElementById('notif-badge');
        if (!badge) return;

        if (count > 0) {
            badge.textContent = count > 99 ? '99+' : count;
            badge.style.display = 'inline-block';
            badge.setAttribute('aria-label', count + ' unread notifications');
        } else {
            badge.style.display = 'none';
            badge.textContent = '0';
        }
    }

    function fetchUnreadCount() {
        if (abortController) {
            abortController.abort();
        }
        abortController = new AbortController();

        fetch(`${CONTEXT_PATH}/notifications/unread-count`, {
            method: 'GET',
            headers: { 'Accept': 'application/json' },
            signal: abortController.signal
        })
            .then(res => {
                if (res.status === 401) {
                    stopPolling();
                    return null;
                }
                return res.ok ? res.json() : null;
            })
            .then(data => {
                if (data && data.success) {
                    updateBadge(data.unreadCount || 0);
                    latestId = data.latestId || 0;
                }
            })
            .catch(err => {
                if (err.name !== 'AbortError') {
                    // Fail silently for background polls
                }
            });
    }

    function loadRecentNotifications() {
        const listEl = document.getElementById('notif-dropdown-list');
        if (!listEl) return;

        listEl.innerHTML = '<div class="notif-loading">Loading...</div>';

        fetch(`${CONTEXT_PATH}/notifications?limit=5`, {
            method: 'GET',
            headers: { 'Accept': 'application/json' }
        })
            .then(res => res.ok ? res.json() : null)
            .then(data => {
                if (!data || !data.notifications || data.notifications.length === 0) {
                    listEl.innerHTML = '<div class="notif-empty-state">No notifications yet.</div>';
                    return;
                }

                updateBadge(data.unreadCount || 0);
                listEl.innerHTML = '';
                data.notifications.forEach(item => {
                    const row = document.createElement('div');
                    row.className = `notif-dropdown-item ${item.read ? 'read' : 'unread'}`;
                    row.innerHTML = `
                        <div class="notif-item-header">
                            <span class="notif-item-title">${escapeHtml(item.title)}</span>
                            <span class="notif-item-time">${formatTimeAgo(item.createdAt)}</span>
                        </div>
                        <div class="notif-item-msg">${escapeHtml(item.message)}</div>
                    `;

                    row.addEventListener('click', function () {
                        if (!item.read) {
                            markSingleRead(item.notificationId, () => {
                                row.classList.remove('unread');
                                row.classList.add('read');
                                if (item.actionUrl) {
                                    window.location.href = item.actionUrl;
                                }
                            });
                        } else if (item.actionUrl) {
                            window.location.href = item.actionUrl;
                        }
                    });

                    listEl.appendChild(row);
                });
            })
            .catch(() => {
                listEl.innerHTML = '<div class="notif-error-state">Failed to load notifications.</div>';
            });
    }

    function markSingleRead(notificationId, callback) {
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
                    updateBadge(data.unreadCount);
                    if (callback) callback();
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
            body: JSON.stringify({ upToId: latestId })
        })
            .then(res => res.ok ? res.json() : null)
            .then(data => {
                if (data && data.success) {
                    updateBadge(0);
                    loadRecentNotifications();
                    window.dispatchEvent(new CustomEvent('notifications:refresh'));
                }
            })
            .catch(console.error);
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

    function formatTimeAgo(timestampStr) {
        if (!timestampStr) return '';
        const date = new Date(timestampStr);
        const now = new Date();
        const diffMs = now - date;
        const diffSec = Math.floor(diffMs / 1000);
        if (diffSec < 60) return 'Just now';
        const diffMin = Math.floor(diffSec / 60);
        if (diffMin < 60) return `${diffMin}m ago`;
        const diffHours = Math.floor(diffMin / 60);
        if (diffHours < 24) return `${diffHours}h ago`;
        const diffDays = Math.floor(diffHours / 24);
        if (diffDays < 7) return `${diffDays}d ago`;
        return date.toLocaleDateString();
    }

    // Auto-init on DOMContentLoaded
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initNotificationBell);
    } else {
        initNotificationBell();
    }
})();
