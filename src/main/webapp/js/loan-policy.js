/**
 * PSG iTech Library - Feature 12: Dynamic Borrowing Policy Controller
 */
(function () {
    'use strict';

    const state = {
        currentUser: null,
        settings: null,
        currentVersion: 1,
        highDemandPage: 1,
        highDemandPageSize: 10,
        highDemandTotal: 0,
        historyPage: 1,
        historyPageSize: 10,
        historyTotal: 0
    };

    function showBanner(type, message) {
        const banner = document.getElementById('feedbackBanner');
        if (!banner) return;
        banner.className = 'alert ' + (type === 'success' ? 'alert-success' : 'alert-danger');
        banner.textContent = message;
        banner.style.display = 'block';
        setTimeout(() => {
            banner.style.display = 'none';
        }, 6000);
    }

    function checkUserRole() {
        fetch('profile', { credentials: 'same-origin' })
            .then(res => {
                if (res.status === 401) {
                    window.location.href = 'login.html?redirect=loan-policy.html';
                    return null;
                }
                return res.json();
            })
            .then(data => {
                if (!data) return;
                const user = data.user || data;
                state.currentUser = user;

                const nameEl = document.getElementById('userName');
                const avatarEl = document.getElementById('userAvatar');
                const roleBadge = document.getElementById('userRoleBadge');

                if (nameEl && user.name) nameEl.textContent = user.name;
                if (avatarEl && user.name) avatarEl.textContent = user.name.charAt(0).toUpperCase();

                const role = (user.role || '').toUpperCase();
                if (roleBadge) {
                    roleBadge.textContent = role === 'ADMIN' ? 'ADMINISTRATOR' : (role === 'LIBRARIAN' ? 'LIBRARIAN' : role);
                }

                if (role !== 'ADMIN') {
                    // Read-only view for Librarians
                    const btnSave = document.getElementById('btnSavePolicy');
                    if (btnSave) {
                        btnSave.disabled = true;
                        btnSave.title = "Only Administrators can modify policy settings.";
                    }
                    const addWinBtn = document.getElementById('btnAddWindow');
                    if (addWinBtn) addWinBtn.disabled = true;

                    const inputs = document.querySelectorAll('#policyForm input, #createWindowForm input');
                    inputs.forEach(input => input.disabled = true);
                }
            })
            .catch(err => {
                console.error("Error loading profile:", err);
            });
    }

    function fetchSettings() {
        const conflictBanner = document.getElementById('conflictBanner');
        if (conflictBanner) conflictBanner.style.display = 'none';

        fetch('admin/loan-policy', { credentials: 'same-origin' })
            .then(res => {
                if (!res.ok) throw new Error("HTTP " + res.status);
                return res.json();
            })
            .then(res => {
                if (!res.success || !res.data) return;
                const s = res.data;
                state.settings = s;
                state.currentVersion = s.version || 1;

                const enabledCheckbox = document.getElementById('policyEnabled');
                const enterInput = document.getElementById('enterThreshold');
                const exitInput = document.getElementById('exitThreshold');
                const daysInput = document.getElementById('highDemandDays');
                const statusBadge = document.getElementById('policyStatusBadge');
                const versionBadge = document.getElementById('versionIndicator');

                if (enabledCheckbox) enabledCheckbox.checked = s.enabled;
                if (enterInput) enterInput.value = s.enterThreshold;
                if (exitInput) exitInput.value = s.exitThreshold;
                if (daysInput) daysInput.value = s.highDemandDays;

                if (versionBadge) versionBadge.textContent = "Version: v" + s.version;
                if (statusBadge) {
                    if (s.enabled) {
                        statusBadge.className = 'badge badge-active';
                        statusBadge.textContent = 'POLICY ACTIVE';
                    } else {
                        statusBadge.className = 'badge badge-inactive';
                        statusBadge.textContent = 'POLICY DISABLED';
                    }
                }
            })
            .catch(err => {
                console.error("Error fetching settings:", err);
                showBanner('danger', "Failed to load policy settings.");
            });
    }

    function saveSettings(e) {
        if (e) e.preventDefault();

        const enabled = document.getElementById('policyEnabled').checked;
        const enterThreshold = parseInt(document.getElementById('enterThreshold').value, 10);
        const exitThreshold = parseInt(document.getElementById('exitThreshold').value, 10);
        const highDemandDays = parseInt(document.getElementById('highDemandDays').value, 10);

        if (exitThreshold >= enterThreshold) {
            showBanner('danger', "Validation Error: Exit threshold (" + exitThreshold + ") must be strictly less than Enter threshold (" + enterThreshold + ").");
            return;
        }

        const payload = {
            enabled: enabled,
            enterThreshold: enterThreshold,
            exitThreshold: exitThreshold,
            highDemandDays: highDemandDays,
            expectedVersion: state.currentVersion
        };

        const btnSave = document.getElementById('btnSavePolicy');
        if (btnSave) btnSave.disabled = true;

        fetch('admin/loan-policy/update', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
            credentials: 'same-origin'
        })
            .then(res => {
                if (res.status === 409) {
                    const conflictBanner = document.getElementById('conflictBanner');
                    if (conflictBanner) conflictBanner.style.display = 'block';
                    throw new Error("Version Conflict");
                }
                return res.json().then(data => ({ status: res.status, ok: res.ok, data: data }));
            })
            .then(result => {
                if (btnSave) btnSave.disabled = false;
                if (result.ok && result.data.success) {
                    showBanner('success', "Settings saved successfully! Updated to Version v" + result.data.data.version);
                    state.currentVersion = result.data.data.version;
                    fetchSettings();
                    fetchHistory();
                    fetchHighDemandBooks();
                } else {
                    showBanner('danger', result.data.message || "Failed to update settings.");
                }
            })
            .catch(err => {
                if (btnSave) btnSave.disabled = false;
                if (err.message !== "Version Conflict") {
                    console.error("Error saving settings:", err);
                    showBanner('danger', "Network error saving settings: " + err.message);
                }
            });
    }

    function runPreview() {
        const enter = document.getElementById('enterThreshold').value;
        const exit = document.getElementById('exitThreshold').value;
        const days = document.getElementById('highDemandDays').value;

        const summaryEl = document.getElementById('previewSummary');
        if (summaryEl) summaryEl.textContent = "Simulating policy impact across inventory...";

        fetch(`admin/loan-policy/preview?enterThreshold=${enter}&exitThreshold=${exit}&highDemandDays=${days}`, {
            credentials: 'same-origin'
        })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    if (summaryEl) {
                        summaryEl.innerHTML = `<strong>Simulation Result:</strong> <span style="color: var(--primary); font-weight: 700;">${data.booksInHighDemand}</span> active book(s) meet the proposed High-Demand criteria.`;
                    }
                } else {
                    if (summaryEl) summaryEl.textContent = "Simulation failed: " + data.message;
                }
            })
            .catch(err => {
                if (summaryEl) summaryEl.textContent = "Simulation request error: " + err.message;
            });
    }

    function fetchHighDemandBooks() {
        const tbody = document.getElementById('highDemandTableBody');
        const countBadge = document.getElementById('highDemandCountBadge');
        const summary = document.getElementById('highDemandPaginationSummary');
        const btnPrev = document.getElementById('btnPrevHighDemand');
        const btnNext = document.getElementById('btnNextHighDemand');

        fetch(`admin/loan-policy/high-demand?page=${state.highDemandPage}&pageSize=${state.highDemandPageSize}`, {
            credentials: 'same-origin'
        })
            .then(res => res.json())
            .then(res => {
                if (!res.success || !res.data) return;
                const items = res.data.items || [];
                const total = res.data.totalCount || 0;
                state.highDemandTotal = total;

                if (countBadge) countBadge.textContent = total + " Books";

                if (items.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px; color: var(--text-secondary);">No books currently under high-demand throttling.</td></tr>';
                    if (summary) summary.textContent = "Showing 0 of 0 items";
                    if (btnPrev) btnPrev.disabled = true;
                    if (btnNext) btnNext.disabled = true;
                    return;
                }

                tbody.innerHTML = items.map(b => {
                    const enteredDate = b.entered_at ? new Date(b.entered_at).toLocaleString() : 'N/A';
                    return `
                        <tr style="border-bottom: 1px solid var(--border);">
                            <td style="padding: 10px 14px; font-weight: 600;">#${b.book_id}</td>
                            <td style="padding: 10px 14px;">
                                <a href="book-details.html?id=${b.book_id}" style="font-weight: 600; color: var(--primary); text-decoration: none;">
                                    ${escapeHtml(b.title || '')}
                                </a>
                                <div style="font-size: 0.8rem; color: var(--text-secondary);">${escapeHtml(b.author || '')}</div>
                            </td>
                            <td style="padding: 10px 14px; font-size: 0.85rem; color: var(--text-secondary);">
                                <div>${escapeHtml(b.category || 'General')}</div>
                                <code>${escapeHtml(b.isbn || '')}</code>
                            </td>
                            <td style="padding: 10px 14px; text-align: center;">
                                <span class="badge badge-reserved" style="font-weight: 700; font-size: 0.85rem;">${b.demand_count}</span>
                            </td>
                            <td style="padding: 10px 14px; text-align: center; font-weight: 600;">
                                ${b.available_copies} / ${b.total_copies}
                            </td>
                            <td style="padding: 10px 14px; text-align: center;">
                                <span class="badge badge-high-demand">${escapeHtml(b.policy_code || 'HIGH_DEMAND')}</span>
                            </td>
                            <td style="padding: 10px 14px; font-size: 0.82rem; color: var(--text-secondary);">${enteredDate}</td>
                        </tr>
                    `;
                }).join('');

                const start = (state.highDemandPage - 1) * state.highDemandPageSize + 1;
                const end = Math.min(state.highDemandPage * state.highDemandPageSize, total);
                if (summary) summary.textContent = `Showing ${start}-${end} of ${total} items`;

                if (btnPrev) btnPrev.disabled = state.highDemandPage <= 1;
                if (btnNext) btnNext.disabled = end >= total;
            })
            .catch(err => {
                console.error("Error loading high demand books:", err);
                tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 20px; color: var(--danger);">Failed to load books.</td></tr>';
            });
    }

    function fetchWindows() {
        const tbody = document.getElementById('windowsTableBody');
        fetch('admin/loan-policy/windows', { credentials: 'same-origin' })
            .then(res => res.json())
            .then(res => {
                if (!res.success || !res.data) return;
                const windows = res.data || [];

                if (windows.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; padding: 16px; color: var(--text-secondary);">No exam windows scheduled.</td></tr>';
                    return;
                }

                tbody.innerHTML = windows.map(w => {
                    const activeBadge = w.active ? '<span class="badge badge-active">Active</span>' : '<span class="badge badge-inactive">Inactive</span>';
                    const deactivateBtn = (w.active && state.currentUser && state.currentUser.role === 'ADMIN')
                        ? `<button class="btn btn-secondary btn-sm" onclick="window.LoanPolicyApp.deactivateWindow(${w.windowId})" style="padding: 2px 8px; font-size: 0.75rem;">Deactivate</button>`
                        : '-';

                    return `
                        <tr style="border-bottom: 1px solid var(--border);">
                            <td style="padding: 8px 12px; font-weight: 600;">${escapeHtml(w.name)}</td>
                            <td style="padding: 8px 12px; font-size: 0.8rem; color: var(--text-secondary);">${w.startDate} &rarr; ${w.endDate}</td>
                            <td style="padding: 8px 12px; text-align: center; font-weight: 700;">&ge; ${w.enterThresholdOverride}</td>
                            <td style="padding: 8px 12px; text-align: center;">${activeBadge}</td>
                            <td style="padding: 8px 12px; text-align: center;">${deactivateBtn}</td>
                        </tr>
                    `;
                }).join('');
            })
            .catch(err => {
                console.error("Error loading windows:", err);
            });
    }

    function createWindow(e) {
        if (e) e.preventDefault();

        const name = document.getElementById('winName').value.trim();
        const start = document.getElementById('winStartDate').value;
        const end = document.getElementById('winEndDate').value;
        const override = parseInt(document.getElementById('winOverride').value, 10);

        if (!name || !start || !end) {
            showBanner('danger', "All window fields are required.");
            return;
        }

        const payload = {
            name: name,
            startDate: start,
            endDate: end,
            enterThresholdOverride: override
        };

        fetch('admin/loan-policy/windows/create', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
            credentials: 'same-origin'
        })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    showBanner('success', "Exam window scheduled successfully.");
                    document.getElementById('createWindowForm').reset();
                    fetchWindows();
                } else {
                    showBanner('danger', data.message || "Failed to schedule window.");
                }
            })
            .catch(err => {
                showBanner('danger', "Error: " + err.message);
            });
    }

    function deactivateWindow(windowId) {
        if (!confirm("Are you sure you want to deactivate this exam window?")) return;

        fetch('admin/loan-policy/windows/deactivate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ windowId: windowId }),
            credentials: 'same-origin'
        })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    showBanner('success', "Exam window deactivated.");
                    fetchWindows();
                } else {
                    showBanner('danger', data.message || "Failed to deactivate window.");
                }
            })
            .catch(err => {
                showBanner('danger', "Error: " + err.message);
            });
    }

    function fetchHistory() {
        const tbody = document.getElementById('historyTableBody');
        const summary = document.getElementById('historyPaginationSummary');
        const btnPrev = document.getElementById('btnPrevHistory');
        const btnNext = document.getElementById('btnNextHistory');

        fetch(`admin/loan-policy/history?page=${state.historyPage}&pageSize=${state.historyPageSize}`, {
            credentials: 'same-origin'
        })
            .then(res => res.json())
            .then(res => {
                if (!res.success || !res.data) return;
                const items = res.data.items || [];
                const total = res.data.totalCount || 0;
                state.historyTotal = total;

                if (items.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 20px; color: var(--text-secondary);">No configuration changes recorded yet.</td></tr>';
                    if (summary) summary.textContent = "Showing 0 of 0 records";
                    if (btnPrev) btnPrev.disabled = true;
                    if (btnNext) btnNext.disabled = true;
                    return;
                }

                tbody.innerHTML = items.map(h => {
                    const changeDate = h.changedAt ? new Date(h.changedAt).toLocaleString() : 'N/A';
                    return `
                        <tr style="border-bottom: 1px solid var(--border);">
                            <td style="padding: 10px 12px; text-align: center; font-weight: 700;">v${h.versionAfter}</td>
                            <td style="padding: 10px 12px;">${escapeHtml(h.adminName || 'Admin')}</td>
                            <td style="padding: 10px 12px; text-align: center;">
                                ${h.enabledBefore !== h.enabledAfter ? `<code>${h.enabledBefore ? 'ON' : 'OFF'} &rarr; ${h.enabledAfter ? 'ON' : 'OFF'}</code>` : (h.enabledAfter ? 'Enabled' : 'Disabled')}
                            </td>
                            <td style="padding: 10px 12px; text-align: center;">
                                ${h.enterBefore !== h.enterAfter ? `<code>${h.enterBefore} &rarr; ${h.enterAfter}</code>` : h.enterAfter}
                            </td>
                            <td style="padding: 10px 12px; text-align: center;">
                                ${h.exitBefore !== h.exitAfter ? `<code>${h.exitBefore} &rarr; ${h.exitAfter}</code>` : h.exitAfter}
                            </td>
                            <td style="padding: 10px 12px; text-align: center;">
                                ${h.daysBefore !== h.daysAfter ? `<code>${h.daysBefore}d &rarr; ${h.daysAfter}d</code>` : `${h.daysAfter}d`}
                            </td>
                            <td style="padding: 10px 12px; font-size: 0.82rem; color: var(--text-secondary);">${changeDate}</td>
                        </tr>
                    `;
                }).join('');

                const start = (state.historyPage - 1) * state.historyPageSize + 1;
                const end = Math.min(state.historyPage * state.historyPageSize, total);
                if (summary) summary.textContent = `Showing ${start}-${end} of ${total} records`;

                if (btnPrev) btnPrev.disabled = state.historyPage <= 1;
                if (btnNext) btnNext.disabled = end >= total;
            })
            .catch(err => {
                console.error("Error loading history:", err);
            });
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function initListeners() {
        const policyForm = document.getElementById('policyForm');
        if (policyForm) policyForm.addEventListener('submit', saveSettings);

        const btnPreview = document.getElementById('btnRunPreview');
        if (btnPreview) btnPreview.addEventListener('click', runPreview);

        const btnRefresh = document.getElementById('btnRefreshPolicy');
        if (btnRefresh) {
            btnRefresh.addEventListener('click', () => {
                fetchSettings();
                fetchHighDemandBooks();
                fetchWindows();
                fetchHistory();
            });
        }

        const winForm = document.getElementById('createWindowForm');
        if (winForm) winForm.addEventListener('submit', createWindow);

        const btnPrevHD = document.getElementById('btnPrevHighDemand');
        if (btnPrevHD) {
            btnPrevHD.addEventListener('click', () => {
                if (state.highDemandPage > 1) {
                    state.highDemandPage--;
                    fetchHighDemandBooks();
                }
            });
        }

        const btnNextHD = document.getElementById('btnNextHighDemand');
        if (btnNextHD) {
            btnNextHD.addEventListener('click', () => {
                state.highDemandPage++;
                fetchHighDemandBooks();
            });
        }

        const btnPrevHist = document.getElementById('btnPrevHistory');
        if (btnPrevHist) {
            btnPrevHist.addEventListener('click', () => {
                if (state.historyPage > 1) {
                    state.historyPage--;
                    fetchHistory();
                }
            });
        }

        const btnNextHist = document.getElementById('btnNextHistory');
        if (btnNextHist) {
            btnNextHist.addEventListener('click', () => {
                state.historyPage++;
                fetchHistory();
            });
        }
    }

    document.addEventListener('DOMContentLoaded', () => {
        checkUserRole();
        initListeners();
        fetchSettings();
        fetchHighDemandBooks();
        fetchWindows();
        fetchHistory();
    });

    window.LoanPolicyApp = {
        fetchSettings,
        deactivateWindow
    };

})();
