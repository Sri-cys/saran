/**
 * Concurrency Activity Monitor JavaScript
 * Periodically fetches operational metrics via Admin API.
 * Strict DOM textContent updates, visibility-aware pausing, and request-overlap protection.
 */
(function() {
    'use strict';

    const REFRESH_INTERVAL_MS = 5000;
    let refreshTimer = null;
    let isRequestInFlight = false;
    let currentAbortController = null;

    document.addEventListener('DOMContentLoaded', initMonitor);

    function initMonitor() {
        fetchConcurrencyMetrics();

        // Setup 5-second recurring polling
        startAutoRefresh();

        // Pause polling when tab is hidden, resume immediately when tab is visible
        document.addEventListener('visibilitychange', function() {
            if (document.visibilityState === 'hidden') {
                stopAutoRefresh();
                updateRefreshIndicator(false, 'Paused (tab hidden)');
            } else {
                updateRefreshIndicator(true, 'Live (5s auto-refresh)');
                fetchConcurrencyMetrics();
                startAutoRefresh();
            }
        });

        // Abort in-flight requests on page unload
        window.addEventListener('beforeunload', function() {
            stopAutoRefresh();
            if (currentAbortController) {
                currentAbortController.abort();
            }
        });

        // Setup profile dropdown trigger
        const profileTrigger = document.getElementById('profileTrigger');
        const profileDropdown = document.getElementById('profileDropdownMenu');
        if (profileTrigger && profileDropdown) {
            profileTrigger.addEventListener('click', function(e) {
                e.stopPropagation();
                profileDropdown.classList.toggle('show');
            });
            document.addEventListener('click', function() {
                profileDropdown.classList.remove('show');
            });
        }
    }

    function startAutoRefresh() {
        stopAutoRefresh();
        refreshTimer = setInterval(function() {
            if (document.visibilityState === 'visible') {
                fetchConcurrencyMetrics();
            }
        }, REFRESH_INTERVAL_MS);
    }

    function stopAutoRefresh() {
        if (refreshTimer) {
            clearInterval(refreshTimer);
            refreshTimer = null;
        }
    }

    function updateRefreshIndicator(isActive, text) {
        const indicator = document.getElementById('refreshStatus');
        if (!indicator) return;
        indicator.textContent = '';

        const dot = document.createElement('span');
        dot.className = 'pulse-dot';
        dot.style.backgroundColor = isActive ? '#38a169' : '#a0aec0';

        const span = document.createElement('span');
        span.textContent = text;

        indicator.appendChild(dot);
        indicator.appendChild(span);
    }

    window.fetchConcurrencyMetrics = function() {
        if (isRequestInFlight) {
            // Skip tick if previous request is still pending
            return;
        }

        isRequestInFlight = true;
        currentAbortController = new AbortController();

        fetch('api/admin/concurrency/status', {
            method: 'GET',
            headers: {
                'Accept': 'application/json'
            },
            signal: currentAbortController.signal
        })
        .then(function(response) {
            if (response.status === 401) {
                window.location.href = 'login.html?redirect=concurrency-monitor.html';
                throw new Error('Authentication required');
            }
            if (response.status === 403) {
                alert('Access Denied: Administrator role required.');
                window.location.href = 'login.html';
                throw new Error('Access forbidden');
            }
            if (!response.ok) {
                throw new Error('Server returned HTTP ' + response.status);
            }
            return response.json();
        })
        .then(function(json) {
            if (json && json.success && json.data) {
                renderDashboard(json.data);
            }
        })
        .catch(function(err) {
            if (err.name !== 'AbortError') {
                console.error('Failed to fetch concurrency metrics:', err);
                updateRefreshIndicator(false, 'Disconnected (retrying...)');
            }
        })
        .finally(function() {
            isRequestInFlight = false;
            currentAbortController = null;
        });
    };

    function renderDashboard(data) {
        // Governor Status & Config
        const badge = document.getElementById('governorStatusBadge');
        if (badge) {
            badge.textContent = data.limiterEnabled ? 'ENABLED' : 'DISABLED';
            badge.className = data.limiterEnabled ? 'badge badge-active' : 'badge badge-danger';
        }

        const sinceEl = document.getElementById('sinceTimestamp');
        if (sinceEl && data.since) {
            try {
                const date = new Date(data.since);
                sinceEl.textContent = 'Active since: ' + date.toLocaleTimeString() + ' (' + date.toLocaleDateString() + ')';
            } catch(e) {
                sinceEl.textContent = 'Active since: ' + data.since;
            }
        }

        if (data.config) {
            setText('pillMutations', 'Capacity: ' + data.config.maxConcurrentMutations);
            setText('pillWaiting', 'Waiting Room: ' + data.config.maxWaitingMutations);
            setText('pillTimeout', 'Wait Timeout: ' + data.config.waitTimeoutMs + 'ms');
            setText('pillUserCap', 'Per-User Cap: ' + data.config.userInflightCap);
        }

        // Live Gauges
        if (data.current) {
            setText('valActive', data.current.active);
            setText('subActive', 'Max observed: ' + data.current.maxActiveObserved);

            setText('valWaiting', data.current.waiting);
            setText('subWaiting', 'Max observed: ' + data.current.maxWaitingObserved);

            setText('valPermits', data.current.availablePermits);
        }

        // Overload Rejections
        if (data.totals && data.totals.rejected) {
            const r = data.totals.rejected;
            const totalRejections = (r.waitTimeout || 0) + (r.waitingRoomFull || 0) + (r.userCap || 0) + (r.poolExhausted || 0) + (r.interrupted || 0);
            setText('valRejections', totalRejections);

            setText('rejectionsDetail',
                'Rejections Breakdown — Wait Timeout: ' + (r.waitTimeout || 0) +
                ' | Waiting Room Full: ' + (r.waitingRoomFull || 0) +
                ' | User In-Flight Cap: ' + (r.userCap || 0) +
                ' | Pool Exhausted: ' + (r.poolExhausted || 0) +
                ' | Interrupted: ' + (r.interrupted || 0));
        }

        // Database Concurrency Indicators
        if (data.totals) {
            setText('valAdmitted', data.totals.admitted || 0);
            setText('valCompleted', data.totals.completed || 0);
            setText('valDeadlocks', data.totals.deadlockRetries || 0);
            setText('valLockTimeouts', data.totals.lockWaitTimeouts || 0);
        }

        if (data.timing) {
            setText('valAvgWait', (data.timing.avgAdmissionWaitMs || 0) + ' ms');
            setText('valMaxWait', (data.timing.maxAdmissionWaitMs || 0) + ' ms');
            setText('valAvgProc', (data.timing.avgProcessingMs || 0) + ' ms');
        }

        // Operations Breakdown Table
        renderOperationsTable(data.operations);
    }

    function renderOperationsTable(operations) {
        const tbody = document.getElementById('operationsTableBody');
        if (!tbody) return;

        // Clear existing rows safely
        tbody.textContent = '';

        if (!operations || Object.keys(operations).length === 0) {
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            td.colSpan = 8;
            td.style.textAlign = 'center';
            td.style.color = '#718096';
            td.textContent = 'No operations recorded yet.';
            tr.appendChild(td);
            tbody.appendChild(tr);
            return;
        }

        for (const opKey in operations) {
            if (!operations.hasOwnProperty(opKey)) continue;
            const op = operations[opKey];

            const tr = document.createElement('tr');

            // Category
            const tdKey = document.createElement('td');
            const strong = document.createElement('strong');
            strong.textContent = opKey;
            tdKey.appendChild(strong);
            tr.appendChild(tdKey);

            // Description
            const tdDesc = document.createElement('td');
            tdDesc.textContent = op.description || '';
            tr.appendChild(tdDesc);

            // Active
            const tdActive = document.createElement('td');
            tdActive.className = 'text-right';
            tdActive.textContent = op.active || 0;
            if (op.active > 0) {
                tdActive.style.fontWeight = 'bold';
                tdActive.style.color = 'var(--primary)';
            }
            tr.appendChild(tdActive);

            // Completed
            const tdCompleted = document.createElement('td');
            tdCompleted.className = 'text-right';
            tdCompleted.textContent = op.completed || 0;
            tr.appendChild(tdCompleted);

            // Conflicts
            const tdConflicts = document.createElement('td');
            tdConflicts.className = 'text-right';
            tdConflicts.textContent = op.conflicts || 0;
            if (op.conflicts > 0) {
                tdConflicts.style.color = '#b7791f';
                tdConflicts.style.fontWeight = 'bold';
            }
            tr.appendChild(tdConflicts);

            // Errors
            const tdErrors = document.createElement('td');
            tdErrors.className = 'text-right';
            tdErrors.textContent = op.errors || 0;
            if (op.errors > 0) {
                tdErrors.style.color = 'var(--danger)';
                tdErrors.style.fontWeight = 'bold';
            }
            tr.appendChild(tdErrors);

            // Avg Wait
            const tdWait = document.createElement('td');
            tdWait.className = 'text-right';
            tdWait.textContent = (op.avgWaitMs || 0) + ' ms';
            tr.appendChild(tdWait);

            // Avg Duration
            const tdProc = document.createElement('td');
            tdProc.className = 'text-right';
            tdProc.textContent = (op.avgProcessingMs || 0) + ' ms';
            tr.appendChild(tdProc);

            tbody.appendChild(tr);
        }
    }

    function setText(id, text) {
        const el = document.getElementById(id);
        if (el) {
            el.textContent = text;
        }
    }

})();

