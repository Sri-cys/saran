/**
 * PSG iTech Central Library - Librarian Portal API Client
 * Enforces same-origin credentials, CSRF token attachment, and unified error handling.
 */

export const CTX = window.location.pathname.replace(/\/[^/]*$/, '');

export async function apiFetch(endpoint, options = {}) {
    const url = endpoint.startsWith('http') ? endpoint : (endpoint.startsWith('/') ? (CTX + endpoint) : (CTX + '/' + endpoint));
    const method = (options.method || 'GET').toUpperCase();

    const headers = new Headers(options.headers || {});
    if (!headers.has('Accept')) {
        headers.set('Accept', 'application/json');
    }

    // Attach CSRF token on mutating requests
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
        let token = sessionStorage.getItem('psgitech_csrf_token');
        if (!token) {
            try {
                const tokenRes = await fetch(CTX + '/csrf-token');
                if (tokenRes.ok) {
                    const tokenData = await tokenRes.json();
                    if (tokenData && tokenData.csrfToken) {
                        token = tokenData.csrfToken;
                        sessionStorage.setItem('psgitech_csrf_token', token);
                    }
                }
            } catch (ignored) {}
        }
        if (token) {
            headers.set('X-CSRF-Token', token);
        }
    }

    const config = {
        ...options,
        method,
        headers,
        credentials: 'same-origin'
    };

    const response = await fetch(url, config);

    if (response.status === 401) {
        window.location.href = CTX + '/librarian-login.html?session_expired=true';
        throw new Error('Authentication session expired. Redirecting to login.');
    }

    if (response.status === 403) {
        let errMsg = 'Access Denied: You do not have permission for this action.';
        try {
            const data = await response.json();
            if (data && data.error && data.error.message) errMsg = data.error.message;
        } catch (ignored) {}
        throw new Error(errMsg);
    }

    const json = await response.json().catch(() => null);

    if (!response.ok) {
        const errorMsg = (json && json.error && json.error.message) ? json.error.message : ('HTTP Error ' + response.status);
        const err = new Error(errorMsg);
        err.code = (json && json.error && json.error.code) ? json.error.code : 'HTTP_' + response.status;
        err.status = response.status;
        throw err;
    }

    return json;
}

export function showToast(message, type = 'success') {
    let container = document.getElementById('libToastContainer');
    if (!container) {
        container = document.createElement('div');
        container.id = 'libToastContainer';
        container.style.position = 'fixed';
        container.style.bottom = '24px';
        container.style.right = '24px';
        container.style.zIndex = '3000';
        container.style.display = 'flex';
        container.style.flexDirection = 'column';
        container.style.gap = '8px';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.setAttribute('role', 'alert');
    toast.setAttribute('aria-live', 'polite');
    toast.style.padding = '12px 18px';
    toast.style.borderRadius = '4px';
    toast.style.fontSize = '13px';
    toast.style.fontWeight = '500';
    toast.style.boxShadow = '0 4px 12px rgba(0,0,0,0.15)';
    toast.style.color = '#FFFFFF';
    toast.style.maxWidth = '360px';

    if (type === 'success') {
        toast.style.backgroundColor = '#159B50';
    } else if (type === 'error') {
        toast.style.backgroundColor = '#B91C1C';
    } else {
        toast.style.backgroundColor = '#B45309';
    }

    toast.innerText = message;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transition = 'opacity 0.3s ease';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}
