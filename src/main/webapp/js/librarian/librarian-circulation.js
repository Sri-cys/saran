/**
 * PSG iTech Central Library - Librarian Portal Circulation Module
 * Manages Active Loans, Due-Soon, Overdue Loans, Issues, Returns, Renewals, and Allocations.
 */

import { apiFetch, showToast } from './librarian-api.js';

let currentCircSubtab = 'active';
let expressCurrentStudent = null;
let expressCurrentCopy = null;
let expressActionHistory = [];

export function initCirculationView() {
    loadCirculationTab(currentCircSubtab);
    initExpressDesk();

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
    loadActiveLoansForDueDate,
    openExpressDeskModal,
    closeExpressDeskModal,
    resetExpressDesk,
    searchExpressStudent,
    inspectExpressCopy,
    executeExpressAction
};

// ============================================================================
// EXPRESS DESK RAPID CIRCULATION MODE (Section 6.1)
// ============================================================================
function initExpressDesk() {
    window.addEventListener('keydown', (e) => {
        // Alt + Shift + D
        if (e.altKey && e.shiftKey && (e.key === 'D' || e.key === 'd')) {
            e.preventDefault();
            const modal = document.getElementById('expressDeskModal');
            if (modal && modal.classList.contains('show')) {
                closeExpressDeskModal();
            } else {
                openExpressDeskModal();
            }
        }
        // Esc key inside express desk modal
        if (e.key === 'Escape') {
            const modal = document.getElementById('expressDeskModal');
            if (modal && modal.classList.contains('show')) {
                resetExpressDesk();
            }
        }
    });

    const studInput = document.getElementById('expressStudentInput');
    if (studInput) {
        studInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                searchExpressStudent();
            }
        });
    }

    const copyInput = document.getElementById('expressCopyInput');
    if (copyInput) {
        copyInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                if (!expressCurrentCopy || expressCurrentCopy.identifier !== copyInput.value.trim()) {
                    inspectExpressCopy();
                } else {
                    executeExpressAction();
                }
            }
        });
    }
}

export function openExpressDeskModal() {
    const modal = document.getElementById('expressDeskModal');
    if (modal) {
        modal.classList.add('show');
        setTimeout(() => {
            const input = document.getElementById('expressStudentInput');
            if (input) input.focus();
        }, 100);
    }
}

export function closeExpressDeskModal() {
    const modal = document.getElementById('expressDeskModal');
    if (modal) modal.classList.remove('show');
}

export function resetExpressDesk() {
    expressCurrentStudent = null;
    expressCurrentCopy = null;

    const sIn = document.getElementById('expressStudentInput');
    if (sIn) sIn.value = '';

    const cIn = document.getElementById('expressCopyInput');
    if (cIn) cIn.value = '';

    const sDetails = document.getElementById('expressStudentCardDetails');
    if (sDetails) {
        sDetails.innerHTML = '<div style="text-align: center; color: #94A3B8; padding: 50px 0;">Scan student barcode or enter roll number above.</div>';
    }

    const cDetails = document.getElementById('expressCopyCardDetails');
    if (cDetails) {
        cDetails.innerHTML = '<div style="text-align: center; color: #94A3B8; padding: 35px 0;">Scan physical book copy to auto-detect Issue or Return.</div>';
    }

    const exBtn = document.getElementById('expressExecuteBtn');
    if (exBtn) {
        exBtn.disabled = true;
        exBtn.innerText = 'Confirm Action (Enter ↵)';
        exBtn.style.background = '';
    }

    const banner = document.getElementById('expressHoldBanner');
    if (banner) {
        banner.style.display = 'none';
        banner.innerText = '';
    }

    if (sIn) sIn.focus();
}

export async function searchExpressStudent() {
    const sIn = document.getElementById('expressStudentInput');
    const q = sIn?.value?.trim();
    if (!q) {
        showToast('Please enter or scan student roll number, email, or ID.', 'error');
        return;
    }

    const sDetails = document.getElementById('expressStudentCardDetails');
    if (sDetails) sDetails.innerHTML = '<div style="text-align: center; padding: 30px; color: var(--navy-light);">Looking up borrower card...</div>';

    try {
        const res = await apiFetch(`/api/librarian/circulation/borrower-card?query=${encodeURIComponent(q)}`);
        if (res && res.success && res.data) {
            expressCurrentStudent = res.data;
            renderStudentBorrowerCard(res.data);
            const cIn = document.getElementById('expressCopyInput');
            if (cIn) cIn.focus();
        } else {
            expressCurrentStudent = null;
            if (sDetails) {
                sDetails.innerHTML = `<div style="text-align: center; color: #B91C1C; padding: 30px 0;">
                    Student not found for "${escapeHtml(q)}".
                </div>`;
            }
        }
    } catch (err) {
        expressCurrentStudent = null;
        if (sDetails) {
            sDetails.innerHTML = `<div style="text-align: center; color: #B91C1C; padding: 30px 0;">
                Error: ${escapeHtml(err.message)}
            </div>`;
        }
    }
}

function renderStudentBorrowerCard(student) {
    const sDetails = document.getElementById('expressStudentCardDetails');
    if (!sDetails) return;

    const trust = student.trustStanding || {};
    let tierColor = '#159B50';
    let tierBg = '#E8F5EE';
    if (trust.tier === 'WATCHLIST') { tierColor = '#B45309'; tierBg = '#FEF3C7'; }
    else if (trust.tier === 'RESTRICTED') { tierColor = '#B91C1C'; tierBg = '#FEE2E2'; }
    else if (trust.tier === 'STANDARD') { tierColor = '#2563EB'; tierBg = '#EFF6FF'; }

    const activeCount = student.activeLoansCount || 0;
    const limit = student.borrowingLimit || 3;
    const meterPercent = Math.min(100, Math.round((activeCount / limit) * 100));

    sDetails.innerHTML = `
        <div style="background: white; border: 1px solid #E2E8F0; border-radius: 6px; padding: 12px; margin-bottom: 10px;">
            <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 6px;">
                <div>
                    <strong style="font-size: 14px; color: var(--navy-primary);">${escapeHtml(student.name)}</strong>
                    <div style="font-size: 11px; color: var(--navy-light); font-family: monospace;">${escapeHtml(student.registerNumber)} • ${escapeHtml(student.department)}</div>
                </div>
                <span style="font-size: 11px; font-weight: bold; padding: 2px 8px; border-radius: 4px; background: ${tierBg}; color: ${tierColor};">
                    ${escapeHtml(trust.tier || 'STANDARD')}
                </span>
            </div>
            <div style="font-size: 11px; color: #64748B; margin-bottom: 8px;">
                ${escapeHtml(trust.explanation || '')}
            </div>
            <!-- Eligibility Gauge -->
            <div style="margin-top: 8px; border-top: 1px solid #F1F5F9; padding-top: 8px;">
                <div style="display: flex; justify-content: space-between; font-size: 11px; font-weight: 600; margin-bottom: 4px;">
                    <span>Active Loan Quota:</span>
                    <span style="color: ${student.canBorrow ? 'var(--navy-primary)' : '#B91C1C'};">${activeCount} / ${limit} Books Used</span>
                </div>
                <div style="width: 100%; height: 6px; background: #E2E8F0; border-radius: 3px; overflow: hidden;">
                    <div style="width: ${meterPercent}%; height: 100%; background: ${activeCount >= limit ? '#B91C1C' : '#159B50'};"></div>
                </div>
                ${!student.canBorrow ? '<div style="font-size: 11px; color: #B91C1C; margin-top: 4px; font-weight: 600;">⚠️ Ineligible for new checkout (limit reached or standing restricted).</div>' : ''}
            </div>
        </div>

        <div style="font-size: 11px; font-weight: bold; color: var(--navy-light); margin-bottom: 4px;">Currently Borrowed (${(student.activeLoans || []).length}):</div>
        <div style="max-height: 90px; overflow-y: auto; background: white; border: 1px solid #E2E8F0; border-radius: 6px; padding: 6px;">
            ${(student.activeLoans && student.activeLoans.length > 0) ? student.activeLoans.map(l => `
                <div style="display: flex; justify-content: space-between; padding: 4px 0; border-bottom: 1px solid #F8FAFC; font-size: 11px;">
                    <span style="max-width: 170px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${escapeHtml(l.bookTitle)}">${escapeHtml(l.bookTitle)}</span>
                    <span style="color: ${l.overdue ? '#B91C1C' : '#64748B'}; font-weight: ${l.overdue ? 'bold' : 'normal'};">Due ${escapeHtml(l.dueDate || '')}</span>
                </div>
            `).join('') : '<div style="color: #94A3B8; text-align: center; padding: 8px;">No active loans held.</div>'}
        </div>
    `;
}

export async function inspectExpressCopy() {
    const cIn = document.getElementById('expressCopyInput');
    const id = cIn?.value?.trim();
    if (!id) {
        showToast('Please enter or scan a book copy barcode or ID.', 'error');
        return;
    }

    const cDetails = document.getElementById('expressCopyCardDetails');
    const exBtn = document.getElementById('expressExecuteBtn');
    if (cDetails) cDetails.innerHTML = '<div style="text-align: center; padding: 25px; color: var(--navy-light);">Inspecting copy status...</div>';

    try {
        const res = await apiFetch(`/api/librarian/circulation/copy-inspect?identifier=${encodeURIComponent(id)}`);
        if (res && res.success && res.data) {
            expressCurrentCopy = res.data;
            expressCurrentCopy.identifier = id;
            renderInspectedCopyCard(res.data);
        } else {
            expressCurrentCopy = null;
            if (cDetails) cDetails.innerHTML = `<div style="text-align: center; color: #B91C1C; padding: 25px 0;">Copy not found: "${escapeHtml(id)}"</div>`;
            if (exBtn) { exBtn.disabled = true; exBtn.innerText = 'Confirm Action (Enter ↵)'; }
        }
    } catch (err) {
        expressCurrentCopy = null;
        if (cDetails) cDetails.innerHTML = `<div style="text-align: center; color: #B91C1C; padding: 25px 0;">Error: ${escapeHtml(err.message)}</div>`;
        if (exBtn) { exBtn.disabled = true; exBtn.innerText = 'Confirm Action (Enter ↵)'; }
    }
}

function renderInspectedCopyCard(copy) {
    const cDetails = document.getElementById('expressCopyCardDetails');
    const exBtn = document.getElementById('expressExecuteBtn');
    if (!cDetails || !exBtn) return;

    const action = copy.suggestedAction;
    let actionBg = '#159B50';
    let actionText = 'ISSUE BOOK TO STUDENT';

    if (action === 'RETURN') {
        actionBg = '#1F3A60';
        actionText = 'CHECK IN RETURN';
    } else if (action === 'CONFIRM_PICKUP') {
        actionBg = '#0284C7';
        actionText = 'CONFIRM RESERVATION PICKUP';
    } else if (action === 'BLOCKED') {
        actionBg = '#64748B';
        actionText = 'CIRCULATION BLOCKED';
    }

    cDetails.innerHTML = `
        <div style="background: white; border: 1px solid #E2E8F0; border-radius: 6px; padding: 12px;">
            <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 6px;">
                <div>
                    <strong style="font-size: 14px; color: var(--navy-primary);">${escapeHtml(copy.title)}</strong>
                    <div style="font-size: 11px; color: var(--navy-light);">By ${escapeHtml(copy.author)} • ISBN: ${escapeHtml(copy.isbn || 'N/A')}</div>
                </div>
                <span class="status-pill status-pill-${(copy.status || '').toLowerCase()}" style="font-size: 11px;">
                    ${escapeHtml(copy.status)}
                </span>
            </div>
            <div style="font-family: monospace; font-size: 12px; font-weight: bold; color: var(--navy-primary); background: #F1F5F9; padding: 4px 8px; border-radius: 4px; display: inline-block; margin-bottom: 8px;">
                ${escapeHtml(copy.copyCode || ('ID #' + copy.copyId))}
            </div>

            <!-- Auto-Detected Action Banner -->
            <div style="background: ${action === 'RETURN' ? '#EFF6FF' : '#F0FDF4'}; border: 1px solid ${action === 'RETURN' ? '#BFDBFE' : '#BBF7D0'}; border-radius: 6px; padding: 8px 12px; margin-top: 4px;">
                <div style="font-size: 11px; font-weight: bold; color: ${action === 'RETURN' ? '#1E40AF' : '#166534'};">
                    Auto-Detected Action: ${action}
                </div>
                <div style="font-size: 11px; color: #475569;">
                    ${escapeHtml(copy.actionDescription || '')}
                </div>
                ${copy.borrowerName ? `<div style="font-size: 11px; color: #1E40AF; margin-top: 4px;">Current Borrower: <strong>${escapeHtml(copy.borrowerName)}</strong> (${escapeHtml(copy.borrowerReg)})</div>` : ''}
            </div>
        </div>
    `;

    if (action === 'BLOCKED') {
        exBtn.disabled = true;
        exBtn.innerText = 'Cannot Circulate (Blocked)';
        exBtn.style.background = '#94A3B8';
    } else if (action === 'ISSUE') {
        if (!expressCurrentStudent) {
            exBtn.disabled = true;
            exBtn.innerText = 'Lookup Student in Step 1 First';
            exBtn.style.background = '#94A3B8';
        } else if (!expressCurrentStudent.canBorrow) {
            exBtn.disabled = true;
            exBtn.innerText = 'Student Ineligible for Checkout';
            exBtn.style.background = '#B91C1C';
        } else {
            exBtn.disabled = false;
            exBtn.innerText = `Confirm Issue to ${expressCurrentStudent.registerNumber} (Enter ↵)`;
            exBtn.style.background = actionBg;
        }
    } else {
        // Return or Confirm Pickup
        exBtn.disabled = false;
        exBtn.innerText = `Confirm ${actionText} (Enter ↵)`;
        exBtn.style.background = actionBg;
    }
}

export async function executeExpressAction() {
    if (!expressCurrentCopy) {
        showToast('Please inspect a physical copy first.', 'error');
        return;
    }

    const action = expressCurrentCopy.suggestedAction;
    if (action === 'BLOCKED') return;

    const payload = { action };

    if (action === 'ISSUE') {
        if (!expressCurrentStudent) {
            showToast('Please lookup the borrower student in Step 1.', 'error');
            return;
        }
        payload.registerNumber = expressCurrentStudent.registerNumber;
        payload.copyId = expressCurrentCopy.copyId;
    } else if (action === 'RETURN') {
        payload.copyId = expressCurrentCopy.copyId;
        if (expressCurrentCopy.transactionId) {
            payload.transactionId = expressCurrentCopy.transactionId;
        }
    } else if (action === 'CONFIRM_PICKUP') {
        payload.action = 'ISSUE';
        payload.registerNumber = expressCurrentCopy.allocatedStudentReg || expressCurrentStudent?.registerNumber;
        payload.copyId = expressCurrentCopy.copyId;
    }

    const exBtn = document.getElementById('expressExecuteBtn');
    if (exBtn) { exBtn.disabled = true; exBtn.innerText = 'Processing Transaction...'; }

    try {
        const res = await apiFetch('/api/librarian/circulation/express-action', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (res && res.success) {
            showToast(res.data?.message || 'Transaction committed successfully!', 'success');

            // Handle FIFO auto-allocation hold at desk banner
            const banner = document.getElementById('expressHoldBanner');
            if (res.data?.holdAtDesk && banner) {
                banner.innerText = res.data.alertBanner || `HOLD AT DESK — Allocated to ${res.data.allocatedToStudent}`;
                banner.style.display = 'block';
            } else if (banner) {
                banner.style.display = 'none';
            }

            // Append to action history
            logExpressAction(action, expressCurrentCopy.copyCode || ('#' + expressCurrentCopy.copyId),
                    action === 'ISSUE' ? (expressCurrentStudent?.registerNumber || 'Student') : (expressCurrentCopy.borrowerReg || 'Check-in'),
                    'Success');

            // Refresh student card if student was active
            if (expressCurrentStudent) {
                searchExpressStudent();
            }

            // Clear copy input and prepare for next item
            const cIn = document.getElementById('expressCopyInput');
            if (cIn) {
                cIn.value = '';
                cIn.focus();
            }
            expressCurrentCopy = null;
            const cDetails = document.getElementById('expressCopyCardDetails');
            if (cDetails) {
                cDetails.innerHTML = '<div style="text-align: center; color: #159B50; padding: 35px 0;">✓ Transaction completed! Scan next copy.</div>';
            }
            if (exBtn) {
                exBtn.disabled = true;
                exBtn.innerText = 'Confirm Action (Enter ↵)';
            }

            // Refresh background circulation tab if visible
            loadCirculationTab(currentCircSubtab);

        } else {
            showToast(res.error?.message || 'Transaction failed.', 'error');
            if (exBtn) {
                exBtn.disabled = false;
                exBtn.innerText = 'Retry Action (Enter ↵)';
            }
            logExpressAction(action, expressCurrentCopy.copyCode || ('#' + expressCurrentCopy.copyId),
                    expressCurrentStudent?.registerNumber || 'Student',
                    'Failed: ' + (res.error?.message || 'Error'));
        }
    } catch (err) {
        showToast(err.message, 'error');
        if (exBtn) {
            exBtn.disabled = false;
            exBtn.innerText = 'Retry Action (Enter ↵)';
        }
        logExpressAction(action, expressCurrentCopy.copyCode || ('#' + expressCurrentCopy.copyId),
                expressCurrentStudent?.registerNumber || 'Student',
                'Failed: ' + err.message);
    }
}

function logExpressAction(action, copy, student, status) {
    const timeStr = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    expressActionHistory.unshift({ timeStr, action, copy, student, status });
    if (expressActionHistory.length > 10) {
        expressActionHistory.pop();
    }

    const logBody = document.getElementById('expressActionLogBody');
    const counter = document.getElementById('expressLogCounter');
    if (counter) counter.innerText = `${expressActionHistory.length} operations recorded`;

    if (logBody) {
        logBody.innerHTML = expressActionHistory.map(item => `
            <tr style="border-bottom: 1px solid #F1F5F9;">
                <td style="padding: 6px 10px; font-family: monospace; font-size: 11px; color: #64748B;">${escapeHtml(item.timeStr)}</td>
                <td style="padding: 6px 10px; font-weight: bold; color: ${item.action === 'ISSUE' ? '#159B50' : '#1F3A60'};">${escapeHtml(item.action)}</td>
                <td style="padding: 6px 10px; font-size: 11px;">${escapeHtml(item.copy)} &rarr; ${escapeHtml(item.student)}</td>
                <td style="padding: 6px 10px; font-size: 11px; color: ${item.status === 'Success' ? '#159B50' : '#B91C1C'};">${escapeHtml(item.status)}</td>
            </tr>
        `).join('');
    }
}
