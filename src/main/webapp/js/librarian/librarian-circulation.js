/**
 * PSG iTech Central Library - Librarian Portal Circulation Module
 * Manages Active Loans, Due-Soon, Overdue Loans, Issues, Returns, Renewals, and Allocations.
 */

import { apiFetch, showToast } from './librarian-api.js';

let currentCircSubtab = 'active';

export function initCirculationView() {
    loadCirculationTab(currentCircSubtab);

    document.querySelectorAll('.circ-tab-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.circ-tab-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            currentCircSubtab = btn.dataset.tab;
            loadCirculationTab(currentCircSubtab);
        });
    });

    const issueForm = document.getElementById('issueBookForm');
    if (issueForm) issueForm.addEventListener('submit', handleIssueSubmit);

    const returnForm = document.getElementById('returnBookForm');
    if (returnForm) returnForm.addEventListener('submit', handleReturnSubmit);

    const pickupForm = document.getElementById('confirmPickupForm');
    if (pickupForm) pickupForm.addEventListener('submit', handlePickupSubmit);
}

export async function loadCirculationTab(tab) {
    const tableContainer = document.getElementById('circTableContainer');
    const calContainer = document.getElementById('circCalendarContainer');

    if (tab === 'due-calendar') {
        if (tableContainer) tableContainer.style.display = 'none';
        if (calContainer) {
            calContainer.style.display = 'block';
            if (window.librarianOperations && window.librarianOperations.loadDueCalendar) {
                window.librarianOperations.loadDueCalendar();
            }
        }
        return;
    }

    if (tableContainer) tableContainer.style.display = 'block';
    if (calContainer) calContainer.style.display = 'none';

    const tbody = document.getElementById('circTableBody');
    if (tbody) tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px;">Loading circulation records...</td></tr>';

    let endpoint = '/api/librarian/circulation';
    if (tab === 'due-soon') endpoint += '/due-soon';
    else if (tab === 'overdue') endpoint += '/overdue';
    else if (tab === 'recent-issued') endpoint += '/recent-issued';
    else if (tab === 'recent-returned') endpoint += '/recent-returned';

    try {
        const res = await apiFetch(endpoint);
        if (res && res.success && res.data) {
            renderCirculationTable(res.data, tab);
        }
    } catch (err) {
        if (tbody) tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #B91C1C; padding: 24px;">Failed to load records: ${escapeHtml(err.message)}</td></tr>`;
    }
}

export async function loadActiveLoansForDueDate(dateStr) {
    const tableContainer = document.getElementById('circTableContainer');
    const calContainer = document.getElementById('circCalendarContainer');
    if (tableContainer) tableContainer.style.display = 'block';
    if (calContainer) calContainer.style.display = 'none';

    const tbody = document.getElementById('circTableBody');
    if (tbody) tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px;">Loading loans due on ' + escapeHtml(dateStr) + '...</td></tr>';

    try {
        const res = await apiFetch(`/api/librarian/circulation?dueDate=${encodeURIComponent(dateStr)}`);
        if (res && res.success && res.data) {
            renderCirculationTable(res.data, 'active');
        }
    } catch (err) {
        if (tbody) tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #B91C1C; padding: 24px;">Failed to load records: ${escapeHtml(err.message)}</td></tr>`;
    }
}

function renderCirculationTable(loans, tab) {
    const tbody = document.getElementById('circTableBody');
    if (!tbody) return;

    if (!loans || loans.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px; color: #5F6368;">No records found in this category.</td></tr>';
        return;
    }

    const todayStr = new Date().toISOString().split('T')[0];

    tbody.innerHTML = loans.map(tx => {
        const isOverdue = tx.dueDate && tx.dueDate < todayStr && tx.status === 'ACTIVE';
        const statusBadge = isOverdue ?
            `<span class="stock-pill stock-red">OVERDUE</span>` :
            `<span class="stock-pill ${tx.status === 'ACTIVE' ? 'stock-green' : 'stock-amber'}">${tx.status}</span>`;

        return `
            <tr>
                <td><strong>#${tx.transactionId}</strong></td>
                <td>
                    <div style="font-weight: 600; color: #203B60;">${escapeHtml(tx.bookTitle || 'Book #' + tx.bookId)}</div>
                    <div style="font-size: 11px; color: #5F6368;">Copy: ${escapeHtml(tx.copyCode || 'C-' + tx.copyId)}</div>
                </td>
                <td>
                    <div style="font-weight: 500;">${escapeHtml(tx.studentName || 'Student #' + tx.studentId)}</div>
                    <div style="font-size: 11px; color: #5F6368;">${escapeHtml(tx.studentRegisterNumber || '')} • ${escapeHtml(tx.studentDepartment || '')}</div>
                </td>
                <td>${escapeHtml(tx.issueDate || '')}</td>
                <td>
                    <span style="${isOverdue ? 'color: #B91C1C; font-weight: 700;' : ''}">${escapeHtml(tx.dueDate || '')}</span>
                </td>
                <td>${statusBadge}</td>
                <td style="white-space: nowrap;">
                    ${tx.status === 'ACTIVE' ? `
                        <button class="btn-quick" style="padding: 4px 8px; font-size: 11px;" onclick="window.librarianCirculation.openReturnModal(${tx.transactionId}, '${escapeHtml(tx.copyCode || '')}', '${escapeHtml(tx.studentRegisterNumber || '')}')">Return</button>
                        <button class="btn-quick" style="padding: 4px 8px; font-size: 11px;" onclick="window.librarianCirculation.renewLoan(${tx.transactionId})">Renew</button>
                        <button class="btn-quick" style="padding: 4px 8px; font-size: 11px; color: #203B60; border-color: #203B60;" onclick="window.librarianCirculation.openGoodwillModal(${tx.transactionId}, ${tx.studentId}, '${escapeHtml(tx.studentRegisterNumber || '')}')">Goodwill Ext</button>
                    ` : ''}
                    ${isOverdue ? `
                        <button class="btn-quick" style="padding: 4px 8px; font-size: 11px; color: #B45309; border-color: #B45309;" 
                            onclick="window.librarianCirculation.sendOverdueReminder(${tx.transactionId})">Reminder</button>
                    ` : ''}
                </td>
            </tr>
        `;
    }).join('');
}

export function openIssueModal() {
    openModal('issueBookModal');
}

export async function handleIssueSubmit(e) {
    e.preventDefault();
    const form = e.target;
    const registerNumber = form.registerNumber.value.trim();
    const copyId = parseInt(form.copyId.value.trim());

    try {
        await apiFetch('/api/librarian/circulation/issue', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ registerNumber, copyId })
        });
        showToast(`Book copy #${copyId} issued successfully to ${registerNumber}!`, 'success');
        closeModal('issueBookModal');
        form.reset();
        loadCirculationTab(currentCircSubtab);
    } catch (err) {
        showToast('Failed to issue book: ' + err.message, 'error');
    }
}

export function openReturnModal(transactionId, copyCode, regNo) {
    document.getElementById('returnTransactionId').value = transactionId;
    document.getElementById('returnModalTitle').innerText = `Accept Return for Tx #${transactionId} (${copyCode || ''})`;
    openModal('returnBookModal');
}

export async function handleReturnSubmit(e) {
    e.preventDefault();
    const form = e.target;
    const transactionId = parseInt(form.transactionId.value);
    const condition = form.condition.value;
    const disposition = form.disposition.value;

    try {
        await apiFetch('/api/librarian/circulation/return', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ transactionId, condition, disposition })
        });
        showToast(`Return checked in successfully for transaction #${transactionId}!`, 'success');
        closeModal('returnBookModal');
        loadCirculationTab(currentCircSubtab);
    } catch (err) {
        showToast('Failed to process return: ' + err.message, 'error');
    }
}

export async function renewLoan(transactionId) {
    if (!confirm(`Extend loan duration for transaction #${transactionId} by 14 days?`)) return;

    try {
        const res = await apiFetch('/api/librarian/circulation/renew', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ transactionId })
        });
        showToast(`Loan renewed successfully! New due date: ${res.data.dueDate}`, 'success');
        loadCirculationTab(currentCircSubtab);
    } catch (err) {
        showToast('Renewal failed: ' + err.message, 'error');
    }
}

export function openPickupModal() {
    openModal('confirmPickupModal');
}

export async function handlePickupSubmit(e) {
    e.preventDefault();
    const form = e.target;
    const copyId = parseInt(form.copyId.value.trim());
    const registerNumber = form.registerNumber.value.trim();

    try {
        await apiFetch('/api/librarian/circulation/confirm-pickup', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ copyId, registerNumber })
        });
        showToast(`Pickup verified! Copy #${copyId} handed over to ${registerNumber}.`, 'success');
        closeModal('confirmPickupModal');
        form.reset();
        loadCirculationTab(currentCircSubtab);
    } catch (err) {
        showToast('Failed to verify allocation pickup: ' + err.message, 'error');
    }
}

export async function sendOverdueReminder(transactionId) {
    try {
        await apiFetch('/api/librarian/circulation/overdue-reminder', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ transactionId })
        });
        showToast(`Overdue reminder dispatched to student for transaction #${transactionId}.`, 'success');
    } catch (err) {
        showToast(err.message, 'error');
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

function escapeHtml(text) {
    if (!text) return '';
    return text.replace(/[&<>"']/g, m => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[m]));
}

export async function openGoodwillModal(transactionId, studentId, regNo) {
    const modal = document.getElementById('goodwillModal');
    if (!modal) return;

    const txInput = document.getElementById('gwTransactionId');
    const regInput = document.getElementById('gwRegisterNumber');
    const notice = document.getElementById('gwEligibilityNotice');
    const reasonInput = document.getElementById('gwReason');
    const daysInput = document.getElementById('gwExtraDays');

    if (txInput) txInput.value = transactionId || '';
    if (regInput) {
        regInput.value = regNo || (studentId ? 'ID #' + studentId : '');
        regInput.dataset.studentId = studentId || '';
    }
    if (daysInput) daysInput.value = '7';
    if (reasonInput) reasonInput.value = '';

    if (notice) {
        notice.textContent = 'Checking student goodwill eligibility...';
        notice.style.color = 'var(--text-muted)';
    }

    modal.classList.add('show');

    if (studentId) {
        try {
            const res = await apiFetch(`/api/librarian/circulation/goodwill-status?studentId=${studentId}`);
            if (res && res.success && res.data) {
                if (res.data.hasUsed) {
                    notice.textContent = 'Notice: This student has ALREADY used their one-time goodwill extension.';
                    notice.style.color = '#B91C1C';
                    const btn = document.getElementById('btnSubmitGoodwill');
                    if (btn) btn.disabled = true;
                } else {
                    notice.textContent = 'Eligible: Student has not used their lifetime goodwill extension.';
                    notice.style.color = '#159B50';
                    const btn = document.getElementById('btnSubmitGoodwill');
                    if (btn) btn.disabled = false;
                }
            }
        } catch (e) {
            if (notice) notice.textContent = '';
        }
    }
}

export function closeGoodwillModal() {
    closeModal('goodwillModal');
}

export async function submitGoodwillExtension() {
    const txInput = document.getElementById('gwTransactionId');
    const regInput = document.getElementById('gwRegisterNumber');
    const daysInput = document.getElementById('gwExtraDays');
    const reasonInput = document.getElementById('gwReason');

    const transactionId = parseInt(txInput?.value || '0', 10);
    const studentId = parseInt(regInput?.dataset.studentId || '0', 10);
    const extraDays = parseInt(daysInput?.value || '7', 10);
    const reason = reasonInput?.value?.trim() || '';

    if (!transactionId) {
        showToast('Please specify a valid transaction ID.', 'error');
        return;
    }
    if (!studentId) {
        showToast('Could not resolve student ID for this loan.', 'error');
        return;
    }
    if (!reason) {
        showToast('Please provide a mandatory justification reason.', 'error');
        return;
    }

    try {
        const res = await apiFetch('/api/librarian/circulation/goodwill-extension', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ studentId, transactionId, extraDays, reason })
        });
        if (res && res.success) {
            showToast(`Goodwill extension granted! New due date: ${res.data?.newDueDate || 'extended'}.`, 'success');
            closeGoodwillModal();
            loadCirculationTab(currentCircSubtab);
        } else {
            showToast(res.error?.message || 'Failed to grant goodwill extension.', 'error');
        }
    } catch (err) {
        showToast(err.message, 'error');
    }
}

window.librarianCirculation = {
    openIssueModal,
    openReturnModal,
    openPickupModal,
    renewLoan,
    sendOverdueReminder,
    openGoodwillModal,
    closeGoodwillModal,
    submitGoodwillExtension,
    loadCirculationTab,
    loadActiveLoansForDueDate
};
