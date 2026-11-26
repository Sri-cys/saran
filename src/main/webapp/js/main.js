/**
 * PSG iTech Library - Main JavaScript Module
 * Handles Global Navigation, Dropdowns, Search, and Session UI state.
 */

// Global CSRF Protection: Intercept mutating fetch calls to attach X-CSRF-Token
(function () {
    const originalFetch = window.fetch;
    window.fetch = async function (resource, init = {}) {
        const method = (init.method || 'GET').toUpperCase();
        if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
            let token = sessionStorage.getItem('psgitech_csrf_token');
            if (!token) {
                try {
                    const tokenRes = await originalFetch('csrf-token');
                    if (tokenRes.ok) {
                        const tokenData = await tokenRes.json();
                        if (tokenData && tokenData.csrfToken) {
                            token = tokenData.csrfToken;
                            sessionStorage.setItem('psgitech_csrf_token', token);
                        }
                    }
                } catch (e) {}
            }
            if (token) {
                init.headers = init.headers || {};
                if (init.headers instanceof Headers) {
                    if (!init.headers.has('X-CSRF-Token')) {
                        init.headers.append('X-CSRF-Token', token);
                    }
                } else if (Array.isArray(init.headers)) {
                    if (!init.headers.some(h => h[0].toLowerCase() === 'x-csrf-token')) {
                        init.headers.push(['X-CSRF-Token', token]);
                    }
                } else {
                    if (!init.headers['X-CSRF-Token'] && !init.headers['x-csrf-token']) {
                        init.headers['X-CSRF-Token'] = token;
                    }
                }
            }
        }
        return originalFetch(resource, init);
    };
})();

document.addEventListener('DOMContentLoaded', function () {
    initNavigation();
    initScrollButtons();
    initSearchHandler();
    highlightActiveNavigation();
    checkCurrentUserState();
    updateLiveLibraryHours();
    if (typeof initPasswordVisibilityToggles === 'function') {
        initPasswordVisibilityToggles();
    }
});

function updateLiveLibraryHours() {
    const badge = document.getElementById('libraryHoursBadge');
    if (!badge) return;
    try {
        const now = new Date();
        const kolkataOptions = { timeZone: 'Asia/Kolkata', hour12: false, hour: 'numeric', minute: 'numeric', weekday: 'short' };
        const parts = new Intl.DateTimeFormat('en-IN', kolkataOptions).formatToParts(now);
        const hour = parseInt(parts.find(p => p.type === 'hour')?.value || '12', 10);
        const minute = parseInt(parts.find(p => p.type === 'minute')?.value || '0', 10);
        const weekday = parts.find(p => p.type === 'weekday')?.value || '';
        const timeDecimal = hour + (minute / 60.0);

        let isOpen = false;
        let hoursText = '';
        if (weekday === 'Sun') {
            isOpen = (timeDecimal >= 9.0 && timeDecimal < 13.0);
            hoursText = isOpen ? 'Open Today: 9:00 AM - 1:00 PM' : 'Closed Today (Sun 9:00 AM - 1:00 PM)';
        } else {
            isOpen = (timeDecimal >= 8.0 && timeDecimal < 20.0);
            hoursText = isOpen ? 'Open Today: 8:00 AM - 8:00 PM' : 'Closed (Opens 8:00 AM)';
        }

        badge.textContent = hoursText;
        badge.className = isOpen ? 'badge badge-available' : 'badge badge-borrowed';
    } catch (e) {
        // Fallback gracefully
    }
}

// Global Logout click listener
document.addEventListener('click', function (e) {
    const logoutTrigger = e.target.closest('[data-action="logout"], a[href="logout"], a[href$="/logout"]');
    if (logoutTrigger) {
        e.preventDefault();
        if (logoutTrigger.dataset.loggingOut === 'true') return;
        logoutTrigger.dataset.loggingOut = 'true';

        sessionStorage.removeItem('psgitech_user');
        sessionStorage.removeItem('psgitech_csrf_token');
        localStorage.removeItem('psgitech_user');
        localStorage.removeItem('psgitech_remember_me');

        const ctx = (function () {
            const p = window.location.pathname;
            if (p.startsWith('/psgitech-library')) return '/psgitech-library';
            return '';
        })();

        const logoutUrl = ctx ? (ctx + '/logout') : 'logout';
        const fallbackRedirect = ctx ? (ctx + '/login.html?logged_out=true') : 'login.html?logged_out=true';

        fetch(logoutUrl, {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'X-Requested-With': 'XMLHttpRequest'
            }
        })
        .then(async res => {
            let redirectTarget = fallbackRedirect;
            if (res.ok) {
                try {
                    const data = await res.json();
                    if (data && data.redirect) {
                        redirectTarget = data.redirect;
                    }
                } catch (err) {}
            }
            window.location.href = redirectTarget;
        })
        .catch(err => {
            console.error('Logout error:', err);
            window.location.href = fallbackRedirect;
        });
    }
});

/**
 * Automatically highlights the active nav link based on the current page URL.
 */
function highlightActiveNavigation() {
    const path = window.location.pathname;
    const filename = path.substring(path.lastIndexOf('/') + 1) || 'index.html';
    document.querySelectorAll('#mainNavMenu a, .nav-menu a').forEach(link => {
        const href = link.getAttribute('href');
        const dataPage = link.getAttribute('data-page');
        if (href === filename || (dataPage && filename.startsWith(dataPage))) {
            link.classList.add('active');
        } else if (filename === 'student-directory.html' && href && href.includes('student-directory.html')) {
            link.classList.add('active');
        } else if (filename === 'my-books.html' && href && href.includes('my-books.html')) {
            link.classList.add('active');
        } else if (filename === 'my-fines.html' && href && href.includes('my-fines.html')) {
            link.classList.add('active');
        } else if (filename === 'browse.html' && href && href.includes('browse.html')) {
            link.classList.add('active');
        } else {
            // Keep manually assigned active if matches
            if (href !== filename) {
                link.classList.remove('active');
            }
        }
    });
}

window.switchToLibrarianLogin = function (e) {
    if (e && e.preventDefault) e.preventDefault();
    sessionStorage.removeItem('psgitech_user');
    sessionStorage.removeItem('psgitech_csrf_token');
    localStorage.removeItem('psgitech_user');
    localStorage.removeItem('psgitech_remember_me');
    fetch('logout', { method: 'POST' }).finally(() => {
        window.location.href = 'librarian-login.html';
    });
};

/**
 * Initializes navbar dropdowns and mobile toggles.
 */
function initNavigation() {
    // Profile Dropdown Toggle
    const profileTrigger = document.getElementById('profileTrigger');
    const profileDropdown = document.getElementById('profileDropdownMenu');

    if (profileTrigger && profileDropdown) {
        profileTrigger.addEventListener('click', function (e) {
            e.stopPropagation();
            profileDropdown.classList.toggle('show');
        });

        document.addEventListener('click', function (e) {
            if (!profileTrigger.contains(e.target) && !profileDropdown.contains(e.target)) {
                profileDropdown.classList.remove('show');
            }
        });
    }

    // Mobile Hamburger Menu
    const navToggle = document.getElementById('navToggle');
    const navMiddle = document.querySelector('.nav-middle');

    if (navToggle && navMiddle) {
        navToggle.addEventListener('click', function () {
            if (navMiddle.style.display === 'flex') {
                navMiddle.style.display = 'none';
            } else {
                navMiddle.style.display = 'flex';
                navMiddle.style.flexDirection = 'column';
                navMiddle.style.position = 'absolute';
                navMiddle.style.top = '100%';
                navMiddle.style.left = '0';
                navMiddle.style.right = '0';
                navMiddle.style.background = '#FFFFFF';
                navMiddle.style.padding = '16px';
                navMiddle.style.borderBottom = '1px solid #E2E8F0';
                navMiddle.style.boxShadow = '0 4px 10px rgba(0,0,0,0.06)';
            }
        });
    }
}

/**
 * Initializes search bar submission across navbar and hero banner.
 */
function initSearchHandler() {
    const navSearchForm = document.getElementById('navSearchForm');
    if (navSearchForm) {
        navSearchForm.addEventListener('submit', function (e) {
            e.preventDefault();
            const type = document.getElementById('navSearchType')?.value || 'all';
            const query = document.getElementById('navSearchInput')?.value.trim() || '';
            window.location.href = `browse.html?type=${encodeURIComponent(type)}&q=${encodeURIComponent(query)}`;
        });
    }

    const heroSearchForm = document.getElementById('heroSearchForm');
    if (heroSearchForm) {
        heroSearchForm.addEventListener('submit', function (e) {
            e.preventDefault();
            const query = document.getElementById('heroSearchInput')?.value.trim() || '';
            window.location.href = `browse.html?type=all&q=${encodeURIComponent(query)}`;
        });
    }
}

function updateScrollBounds(container) {
    if (!container) return;
    const targetId = container.id;
    const leftBtn = document.querySelector(`[data-scroll-target="${targetId}"][data-scroll-dir="left"]`);
    const rightBtn = document.querySelector(`[data-scroll-target="${targetId}"][data-scroll-dir="right"]`);

    if (leftBtn) {
        const atStart = container.scrollLeft <= 5;
        leftBtn.disabled = atStart;
        leftBtn.style.opacity = atStart ? '0.35' : '1';
        leftBtn.style.cursor = atStart ? 'not-allowed' : 'pointer';
    }
    if (rightBtn) {
        const atEnd = (container.scrollLeft + container.clientWidth) >= (container.scrollWidth - 8);
        rightBtn.disabled = atEnd;
        rightBtn.style.opacity = atEnd ? '0.35' : '1';
        rightBtn.style.cursor = atEnd ? 'not-allowed' : 'pointer';
    }
}
window.updateScrollBounds = updateScrollBounds;

/**
 * Adds horizontal scrolling controls for category and book shelves with bounds checking & keyboard support.
 */
function initScrollButtons() {
    document.querySelectorAll('[data-scroll-target]').forEach(button => {
        button.addEventListener('click', function () {
            const targetId = this.getAttribute('data-scroll-target');
            const direction = this.getAttribute('data-scroll-dir') === 'left' ? -300 : 300;
            const container = document.getElementById(targetId);
            if (container) {
                container.scrollBy({ left: direction, behavior: 'smooth' });
                setTimeout(() => updateScrollBounds(container), 350);
            }
        });
    });

    document.querySelectorAll('.books-scroll-row, .categories-scroll').forEach(container => {
        container.addEventListener('scroll', () => updateScrollBounds(container));
        container.setAttribute('tabindex', '0');
        container.addEventListener('keydown', function(e) {
            if (e.key === 'ArrowRight') {
                e.preventDefault();
                container.scrollBy({ left: 240, behavior: 'smooth' });
                setTimeout(() => updateScrollBounds(container), 350);
            } else if (e.key === 'ArrowLeft') {
                e.preventDefault();
                container.scrollBy({ left: -240, behavior: 'smooth' });
                setTimeout(() => updateScrollBounds(container), 350);
            }
        });
        setTimeout(() => updateScrollBounds(container), 150);
    });
}

/**
 * Handles automatic redirect when a user is on a protected page but has no active backend session.
 */
function handleProtectedPageSessionExpiry() {
    const path = window.location.pathname;
    const filename = path.substring(path.lastIndexOf('/') + 1) || 'index.html';
    const protectedPages = [
        'my-borrowings.html',
        'my-reservations.html',
        'my-fines.html',
        'my-cases.html',
        'student-home.html',
        'profile.html',
        'librarian-dashboard.html',
        'borrowing-management.html',
        'manage-books.html',
        'admin-users.html',
        'loan-policy.html',
        'concurrency-monitor.html'
    ];
    if (protectedPages.includes(filename)) {
        window.location.href = `login.html?expired=true&redirect=${encodeURIComponent(filename)}`;
    }
}

/**
 * Inspects session state and verifies user authentication with backend.
 */
function checkCurrentUserState() {
    let cachedUser = null;
    try {
        const stored = sessionStorage.getItem('psgitech_user') || localStorage.getItem('psgitech_user');
        if (stored) {
            cachedUser = JSON.parse(stored);
        }
    } catch (e) {}

    // Immediate display if remembered for seamless perception
    if (cachedUser && cachedUser.name) {
        updateNavForLoggedInUser(cachedUser);
    } else {
        updateNavForLoggedOutUser();
    }

    // Always authoritative: verify active session with Java backend
    fetch('profile', {
        headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
    })
    .then(async res => {
        if (res.ok) {
            const user = await res.json().catch(() => null);
            if (user && user.name) {
                sessionStorage.setItem('psgitech_user', JSON.stringify(user));
                updateNavForLoggedInUser(user);
                return;
            }
        }
        
        if (res.status === 401) {
            // Actual session is absent or expired on backend
            sessionStorage.removeItem('psgitech_user');
            localStorage.removeItem('psgitech_user');
            updateNavForLoggedOutUser();
            handleProtectedPageSessionExpiry();
            return;
        }

        // Other non-OK status (e.g. 500)
        if (!cachedUser) {
            updateNavForLoggedOutUser();
        }
    })
    .catch(() => {
        // Network offline fallback
        if (cachedUser) {
            updateNavForLoggedInUser(cachedUser);
        } else {
            updateNavForLoggedOutUser();
        }
    });
}

function updateNavForLoggedInUser(user) {
    if (!user) return;

    const loggedOutSection = document.getElementById('navLoggedOut');
    const loggedInSection = document.getElementById('navLoggedIn');
    const navUsername = document.getElementById('navUsername');
    const studentNavName = document.getElementById('studentNavName');
    const studentWelcomeName = document.getElementById('studentWelcomeName');
    const navAvatar = document.getElementById('navAvatar');
    const studentAvatar = document.getElementById('studentAvatar');
    const navDashboardLink = document.getElementById('navDashboardLink');
    const navBrandLogo = document.getElementById('navBrandLogo');
    const navDropdownRoleHeader = document.getElementById('navDropdownRoleHeader');

    // Show logged-in elements, hide logged-out
    if (loggedOutSection) loggedOutSection.style.display = 'none';
    if (loggedInSection) loggedInSection.style.display = 'flex';

    // Update names
    const displayName = user.name || 'User';
    if (navUsername) navUsername.textContent = displayName;
    if (studentNavName) studentNavName.textContent = displayName;
    if (studentWelcomeName) studentWelcomeName.textContent = displayName;

    // Update avatars
    const initial = displayName.charAt(0).toUpperCase() || 'U';
    if (navAvatar) navAvatar.textContent = initial;
    if (studentAvatar) studentAvatar.textContent = initial;

    // Role-specific links and headers
    const staffDeskLink = document.getElementById('navStaffDeskLink');
    const staffManageLink = document.getElementById('navStaffManageLink');
    const adminUsersLink = document.getElementById('navAdminUsersLink');
    const concurrencyLink = document.getElementById('navConcurrencyLink');

    if (user.role === 'ADMIN') {
        if (navDropdownRoleHeader) navDropdownRoleHeader.textContent = 'Administrator';
        if (navDashboardLink) {
            navDashboardLink.href = 'admin-users.html';
            navDashboardLink.textContent = 'Admin Console';
        }
        if (navBrandLogo) navBrandLogo.href = 'admin-users.html';
        if (staffDeskLink) staffDeskLink.style.display = 'block';
        if (staffManageLink) staffManageLink.style.display = 'block';
        if (adminUsersLink) adminUsersLink.style.display = 'block';
        if (concurrencyLink) concurrencyLink.style.display = 'block';
    } else if (user.role === 'LIBRARIAN') {
        if (navDropdownRoleHeader) navDropdownRoleHeader.textContent = 'Librarian Desk';
        if (navDashboardLink) {
            navDashboardLink.href = 'librarian-dashboard.html';
            navDashboardLink.textContent = 'Librarian Dashboard';
        }
        if (navBrandLogo) navBrandLogo.href = 'librarian-dashboard.html';
        if (staffDeskLink) staffDeskLink.style.display = 'block';
        if (staffManageLink) staffManageLink.style.display = 'block';
        if (adminUsersLink) adminUsersLink.style.display = 'none';
        if (concurrencyLink) concurrencyLink.style.display = 'none';
    } else {
        if (navDropdownRoleHeader) navDropdownRoleHeader.textContent = 'Student Portal';
        if (navDashboardLink) {
            navDashboardLink.href = 'student-home.html';
            navDashboardLink.textContent = 'Student Dashboard';
        }
        if (navBrandLogo) navBrandLogo.href = 'index.html';
        if (staffDeskLink) staffDeskLink.style.display = 'none';
        if (staffManageLink) staffManageLink.style.display = 'none';
        if (adminUsersLink) adminUsersLink.style.display = 'none';
        if (concurrencyLink) concurrencyLink.style.display = 'none';
        updateStudentInterchangeNotificationBadge();
    }
}

function updateStudentInterchangeNotificationBadge() {
    const badge = document.querySelector('.notification-badge');
    if (!badge) return;
    fetch('interchanges?type=count')
        .then(r => r.json())
        .then(json => {
            if (json && json.success && json.data) {
                const count = json.data.pendingIncomingCount || 0;
                if (count > 0) {
                    badge.textContent = count;
                    badge.style.display = 'inline-block';
                }
            }
        })
        .catch(() => {});
}

window.openModal = function (modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.add('show');
};

window.closeModal = function (modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.remove('show');
};

window.getCurrentUser = function () {
    try {
        const u = sessionStorage.getItem('psgitech_user') || localStorage.getItem('psgitech_user');
        return u ? JSON.parse(u) : null;
    } catch (e) {
        return null;
    }
};

window.isStaffUser = function () {
    const u = window.getCurrentUser();
    return u && (u.role === 'LIBRARIAN' || u.role === 'ADMIN');
};

window.isStudentUser = function () {
    const u = window.getCurrentUser();
    return u && u.role === 'STUDENT';
};

function updateNavForLoggedOutUser() {
    const loggedOutSection = document.getElementById('navLoggedOut');
    const loggedInSection = document.getElementById('navLoggedIn');
    const navBrandLogo = document.getElementById('navBrandLogo');

    if (loggedOutSection) loggedOutSection.style.display = 'flex';
    if (loggedInSection) loggedInSection.style.display = 'none';
    if (navBrandLogo) navBrandLogo.href = 'index.html';
}
