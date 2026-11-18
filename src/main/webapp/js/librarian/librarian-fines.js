/**
 * PSG iTech Central Library - Librarian Portal Fines Module
 * Read-Only fine audit table with verified payment reference tracing.
 */

import { apiFetch, showToast } from './librarian-api.js';

let currentFinesPage = 1;
let currentFinesPageSize = 20;

export function initFinesView() {
    loadFines();

    const searchInput = document.getElementById('finesSearchInput');
    const filterSelect = document.getElementById('finesStatusSelect');

    const trigger = () => {
        currentFinesPage = 1;
        loadFines();
    };

    if (searchInput) searchInput.addEventListener('input', debounce(trigger, 300));
    if (filterSelect) filterSelect.addEventListener('change', trigger);
}

export async function loadFines() {
    const q = document.getElementById('finesSearchInput')?.value || '';
    const status = document.getElementById('finesStatusSelect')?.value || '';

    const tbody = document.getElementById('finesTableBody');
    if (tbody) tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px;">Loading fine records...</td></tr>';

    try {
        const params = new URLSearchParams({
            page: currentFinesPage,
            pageSize: currentFinesPageSize,
            status: status,
            q: q.trim()
        });

        const res = await apiFetch(`/api/librarian/fines?${params.toString()}`);
        if (res && res.success && res.data) {
            renderFinesTable(res.data);
        }
    } catch (err) {
        if (tbody) tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #B91C1C; padding: 24px;">Failed to load fines: ${escapeHtml(err.message)}</td></tr>`;
    }
}

function renderFinesTable(fines) {
    const tbody = document.getElementById('finesTableBody');
    if (!tbody) return;

    if (!fines || fines.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px; color: #5F6368;">No fine records match your filter criteria.</td></tr>';
        return;
    }

    tbody.innerHTML = fines.map(f => {
        let statusBadge = '<span class="stock-pill stock-red">UNPAID</span>';
        if (f.status === 'PAID') statusBadge = '<span class="stock-pill stock-green">PAID</span>';
        else if (f.status === 'PARTIALLY_PAID') statusBadge = '<span class="stock-pill stock-amber">PARTIALLY PAID</span>';
        else if (f.status === 'WAIVED') statusBadge = '<span class="stock-pill" style="background: #E5E7EB; color: #374151;">WAIVED</span>';

        return `
            <tr>
                <td><strong>#${f.fineId}</strong></td>
                <td>
                    <div style="font-weight: 600; color: #203B60;">${escapeHtml(f.bookTitle)}</div>
                    <div style="font-size: 11px; color: #5F6368;">Tx: #${f.transactionId} • Copy: ${escapeHtml(f.copyCode || '')}</div>
                </td>
                <td>
                    <div style="font-weight: 500;">${escapeHtml(f.studentName)}</div>
                    <div style="font-size: 11px; color: #5F6368;">${escapeHtml(f.registerNumber)}</div>
                </td>
                <td>${f.overdueDays} days</td>
                <td>
                    <div><strong>₹${parseFloat(f.originalAmount || 0).toFixed(2)}</strong></div>
                    <div style="font-size: 11px; color: #5F6368;">Outstanding: ₹${parseFloat(f.outstandingAmount || 0).toFixed(2)}</div>
                </td>
                <td>${statusBadge}</td>
                <td>
                    <div style="font-family: monospace; font-size: 12px; color: #1F3A60;">${escapeHtml(f.paymentReference || 'N/A')}</div>
                    <div style="font-size: 11px; color: #5F6368;">${escapeHtml(f.paymentMethod || 'Unsettled')}</div>
                </td>
            </tr>
        `;
    }).join('');
}

function debounce(fn, ms) {
    let timer;
    return function (...args) {
        clearTimeout(timer);
        timer = setTimeout(() => fn.apply(this, args), ms);
    };
}

function escapeHtml(text) {
    if (!text) return '';
    return text.replace(/[&<>"']/g, m => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[m]));
}
