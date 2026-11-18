/**
 * PSG iTech Central Library - Librarian Portal Queues & Transfers Module
 * Displays FIFO Reservation Queues, 24h Allocation Countdowns, and Peer Transfers.
 */

import { apiFetch } from './librarian-api.js';

let countdownInterval = null;

export function initQueuesView() {
    loadQueuesSubtab('reservations');

    document.querySelectorAll('.queue-subtab-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.queue-subtab-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            const sub = btn.dataset.subtab;
            loadQueuesSubtab(sub);
        });
    });
}

export function loadQueuesSubtab(sub) {
    if (countdownInterval) clearInterval(countdownInterval);

    if (sub === 'transfers') {
        document.getElementById('queuesReservationsPanel').style.display = 'none';
        document.getElementById('queuesAllocationsPanel').style.display = 'none';
        document.getElementById('queuesTransfersPanel').style.display = 'block';
        loadTransfers();
    } else if (sub === 'allocations') {
        document.getElementById('queuesReservationsPanel').style.display = 'none';
        document.getElementById('queuesAllocationsPanel').style.display = 'block';
        document.getElementById('queuesTransfersPanel').style.display = 'none';
        loadAllocations();
    } else {
        document.getElementById('queuesReservationsPanel').style.display = 'block';
        document.getElementById('queuesAllocationsPanel').style.display = 'none';
        document.getElementById('queuesTransfersPanel').style.display = 'none';
        loadReservations();
    }
}

async function loadReservations() {
    const tbody = document.getElementById('reservationsQueueTableBody');
    if (tbody) tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; padding: 24px;">Loading FIFO queues...</td></tr>';

    try {
        const res = await apiFetch('/api/librarian/queues');
        if (res && res.success && res.data) {
            renderReservationsTable(res.data);
        }
    } catch (err) {
        if (tbody) tbody.innerHTML = `<tr><td colspan="5" style="text-align: center; color: #B91C1C; padding: 24px;">Failed: ${escapeHtml(err.message)}</td></tr>`;
    }
}

function renderReservationsTable(reservations) {
    const tbody = document.getElementById('reservationsQueueTableBody');
    if (!tbody) return;

    if (!reservations || reservations.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; padding: 24px; color: #5F6368;">No active waiting reservations in queue.</td></tr>';
        return;
    }

    tbody.innerHTML = reservations.map((r, idx) => `
        <tr>
            <td><strong>#${idx + 1}</strong></td>
            <td>
                <div style="font-weight: 600; color: #203B60;">${escapeHtml(r.bookTitle || 'Book #' + r.bookId)}</div>
                <div style="font-size: 11px; color: #5F6368;">Queue ID: ${r.reservationId}</div>
            </td>
            <td>
                <div style="font-weight: 500;">${escapeHtml(r.studentName || 'Student #' + r.studentId)}</div>
                <div style="font-size: 11px; color: #5F6368;">${escapeHtml(r.studentRegisterNumber || '')}</div>
            </td>
            <td>${r.reservationDate || ''}</td>
            <td><span class="stock-pill stock-amber">${r.status}</span></td>
        </tr>
    `).join('');
}

async function loadAllocations() {
    const container = document.getElementById('allocationsCardGrid');
    if (container) container.innerHTML = '<div style="padding: 24px; text-align: center;">Loading collection pickups...</div>';

    try {
        const res = await apiFetch('/api/librarian/queues/allocations');
        if (res && res.success && res.data) {
            renderAllocationsCards(res.data);
        }
    } catch (err) {
        if (container) container.innerHTML = `<div style="padding: 24px; color: #B91C1C;">Failed: ${escapeHtml(err.message)}</div>`;
    }
}

function renderAllocationsCards(allocations) {
    const container = document.getElementById('allocationsCardGrid');
    if (!container) return;

    if (!allocations || allocations.length === 0) {
        container.innerHTML = '<div style="padding: 24px; color: #5F6368; text-align: center;">No copies currently held for student pickup.</div>';
        return;
    }

    container.innerHTML = allocations.map(a => `
        <div class="kpi-card-box" style="text-align: left; margin-bottom: 12px; border-left: 4px solid ${a.isExpired ? '#B91C1C' : '#159B50'};">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
                <span style="font-weight: bold; color: #203B60; font-size: 15px;">${escapeHtml(a.bookTitle)}</span>
                <span class="stock-pill ${a.isExpired ? 'stock-red' : 'stock-green'}">${a.isExpired ? 'EXPIRED' : 'READY FOR PICKUP'}</span>
            </div>
            <div style="font-size: 12px; color: #5F6368; margin-bottom: 8px;">
                Physical Copy: <strong>${escapeHtml(a.copyCode)}</strong> • Held for Student: <strong>${escapeHtml(a.studentName)} (${escapeHtml(a.registerNumber)})</strong>
            </div>
            <div style="display: flex; justify-content: space-between; align-items: center;">
                <div style="font-size: 13px; font-weight: 600; color: ${a.isExpired ? '#B91C1C' : '#1F3A60'};">
                    Collection Deadline: <span class="countdown-timer" data-seconds="${a.remainingSeconds}">${formatSeconds(a.remainingSeconds)}</span>
                </div>
                ${!a.isExpired ? `
                    <button class="btn-quick btn-quick-primary" style="padding: 4px 10px; font-size: 11px;" 
                        onclick="window.librarianCirculation.openPickupModal()">Confirm Pickup</button>
                ` : ''}
            </div>
        </div>
    `).join('');

    // Start 1-second countdown tick
    countdownInterval = setInterval(() => {
        document.querySelectorAll('.countdown-timer').forEach(el => {
            let secs = parseInt(el.dataset.seconds || 0);
            if (secs > 0) {
                secs--;
                el.dataset.seconds = secs;
                el.innerText = formatSeconds(secs);
            } else {
                el.innerText = 'EXPIRED (Swept to next in queue)';
            }
        });
    }, 1000);
}

async function loadTransfers() {
    const tbody = document.getElementById('transfersTableBody');
    if (tbody) tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px;">Loading peer-to-peer transfers...</td></tr>';

    try {
        const res = await apiFetch('/api/librarian/queues/transfers');
        if (res && res.success && res.data) {
            renderTransfersTable(res.data);
        }
    } catch (err) {
        if (tbody) tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #B91C1C; padding: 24px;">Failed: ${escapeHtml(err.message)}</td></tr>`;
    }
}

function renderTransfersTable(transfers) {
    const tbody = document.getElementById('transfersTableBody');
    if (!tbody) return;

    if (!transfers || transfers.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px; color: #5F6368;">No student-to-student transfer records found.</td></tr>';
        return;
    }

    tbody.innerHTML = transfers.map(t => `
        <tr>
            <td><strong>#${t.interchangeId}</strong></td>
            <td>
                <div style="font-weight: 600; color: #203B60;">${escapeHtml(t.bookTitle || 'Book #' + t.bookId)}</div>
                <div style="font-size: 11px; color: #5F6368;">Copy: ${escapeHtml(t.copyCode || 'C-' + t.copyId)}</div>
            </td>
            <td>${escapeHtml(t.fromStudentName)} (${escapeHtml(t.fromRegisterNumber || '')})</td>
            <td>${escapeHtml(t.toStudentName)} (${escapeHtml(t.toRegisterNumber || '')})</td>
            <td>${escapeHtml(t.requestedAt || '')}</td>
            <td><span class="stock-pill ${t.status === 'COMPLETED' ? 'stock-green' : 'stock-amber'}">${t.status}</span></td>
            <td>
                <span class="stock-pill ${t.otpVerificationStatus === 'Verified' ? 'stock-green' : 'stock-amber'}">
                    ${t.otpVerificationStatus}
                </span>
            </td>
        </tr>
    `).join('');
}

function formatSeconds(totalSeconds) {
    if (totalSeconds <= 0) return '0h 0m 0s (Expired)';
    const hrs = Math.floor(totalSeconds / 3600);
    const mins = Math.floor((totalSeconds % 3600) / 60);
    const secs = totalSeconds % 60;
    return `${hrs}h ${mins}m ${secs}s remaining`;
}

function escapeHtml(text) {
    if (!text) return '';
    return text.replace(/[&<>"']/g, m => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[m]));
}
