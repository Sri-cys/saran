/**
 * PSG iTech Library - Feature 5: Reservation Queue & Automatic Allocation Client Controller
 * Handles student reservations management, live queue positions, allocated collection notices, and release workflows.
 */

document.addEventListener('DOMContentLoaded', function () {
    initMyReservations();
});

function initMyReservations() {
    loadMyReservations();
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

async function loadMyReservations() {
    const activeContainer = document.getElementById('activeReservationsContainer');
    const historyTableBody = document.getElementById('historyTableBody');
    const quotaBadge = document.getElementById('reservationQuotaBadge');

    if (!activeContainer || !historyTableBody) return;

    try {
        const resp = await fetch('reservations/my');
        if (resp.status === 401) {
            window.location.href = 'login.html?redirect=my-reservations.html';
            return;
        }

        const data = await resp.json();
        if (!data.success) {
            activeContainer.innerHTML = `<div class="info-card text-danger" style="padding: 20px;">${escapeHtml(data.message || 'Error loading reservations.')}</div>`;
            return;
        }

        const payload = data.data || {};
        const activeList = payload.activeReservations || [];
        const historyList = payload.reservationHistory || [];
        const summary = payload.summary || { activeCount: 0, maxLimit: 3, remainingCapacity: 3 };

        // Update Quota Summary Badge
        if (quotaBadge) {
            quotaBadge.textContent = `${summary.activeCount} / ${summary.maxLimit} Active Reservations`;
            if (summary.activeCount >= summary.maxLimit) {
                quotaBadge.style.backgroundColor = '#FDE8E8';
                quotaBadge.style.color = 'var(--danger)';
                quotaBadge.style.borderColor = 'var(--danger)';
            } else {
                quotaBadge.style.backgroundColor = '#E8F8F0';
                quotaBadge.style.color = 'var(--success)';
                quotaBadge.style.borderColor = 'var(--success)';
            }
        }

        // Render Active Reservations (Cards)
        if (activeList.length === 0) {
            activeContainer.innerHTML = `
                <div class="empty-state" style="padding: 36px 20px; text-align: center; background: #FFFFFF; border: 1px solid var(--border); border-radius: 8px;">
                    <svg class="empty-state-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" style="width: 48px; height: 48px; margin-bottom: 12px; color: var(--text-muted);">
                        <circle cx="12" cy="12" r="10"></circle>
                        <polyline points="12 6 12 12 16 14"></polyline>
                    </svg>
                    <h3 style="margin-bottom: 6px;">No Active Reservations</h3>
                    <p class="text-secondary" style="margin-bottom: 16px;">You are not currently waiting in line for any library textbooks.</p>
                    <a href="browse.html" class="btn btn-primary btn-sm">Explore Book Catalog</a>
                </div>
            `;
        } else {
            let html = '<div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 20px;">';
            for (const res of activeList) {
                const cover = res.coverImageUrl || res.bookCoverUrl || `https://covers.openlibrary.org/b/isbn/${(res.bookIsbn || '').replace(/[^0-9X]/gi, '')}-L.jpg`;
                const isAllocated = res.status === 'ALLOCATED';
                const pos = res.queuePosition || 1;
                const total = res.totalWaitingInQueue || pos;
                const copyCode = res.copyCode || res.copyIdentifier || '';
                const expiresAt = res.allocationExpiresAt || res.expiresAt || '';

                html += `
                    <div class="info-card" style="display: flex; gap: 16px; align-items: start; background: #FFFFFF; border: 1px solid ${isAllocated ? '#159B50' : 'var(--border)'}; border-radius: 8px; padding: 18px; box-shadow: 0 1px 3px rgba(0,0,0,0.04);">
                        <img src="${escapeHtml(cover)}" alt="${escapeHtml(res.bookTitle || 'Book Cover')}" 
                             style="width: 80px; height: 115px; object-fit: cover; border-radius: 4px; border: 1px solid var(--border); flex-shrink: 0;"
                             onerror="this.onerror=null; this.src='data:image/svg+xml;utf8,<svg xmlns=\\'http://www.w3.org/2000/svg\\' width=\\'80\\' height=\\'115\\' viewBox=\\'0 0 24 24\\' fill=\\'none\\' stroke=\\'%23203B60\\' stroke-width=\\'1.5\\'><path d=\\'M4 19.5A2.5 2.5 0 0 1 6.5 17H20\\'/><path d=\\'M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z\\'/></svg>';">
                        
                        <div style="flex: 1; min-width: 0;">
                            <div style="display: flex; justify-content: space-between; align-items: start; gap: 8px; margin-bottom: 6px;">
                                <h4 style="margin: 0; font-size: 1.05rem; line-height: 1.3;">
                                    <a href="book-details.html?id=${res.bookId}" style="color: var(--primary); text-decoration: none;">
                                        ${escapeHtml(res.bookTitle || 'Untitled Book')}
                                    </a>
                                </h4>
                                <span class="badge" style="${isAllocated ? 'background-color: #E8F8F0; color: #159B50; border: 1px solid #159B50; font-weight: bold;' : 'background-color: #FEF6E8; color: #C58B24; border: 1px solid #C58B24;'}">
                                    ${isAllocated ? 'ALLOCATED - Ready for Collection' : 'WAITING'}
                                </span>
                            </div>

                            <p class="text-secondary" style="font-size: 0.85rem; margin-bottom: 10px;">
                                by ${escapeHtml(res.bookAuthor || 'Unknown')}<br>
                                Reserved on: ${escapeHtml(res.reservationDate || '--')}
                            </p>

                            ${isAllocated ? `
                                <!-- Highlighted Allocation Notice (Feature 5) -->
                                <div style="background-color: #E8F8F0; border-left: 3px solid #159B50; padding: 10px 12px; border-radius: 4px; margin-bottom: 14px;">
                                    <div style="font-size: 0.9rem; font-weight: bold; color: #159B50; margin-bottom: 4px;">
                                        ✓ Ready for Collection
                                    </div>
                                    <div style="font-size: 0.85rem; color: #222222;">
                                        Physical Copy: <code>${escapeHtml(copyCode)}</code>
                                    </div>
                                    <div style="font-size: 0.82rem; color: #444444; margin-top: 3px;">
                                        Collect from the library desk by: <strong>${escapeHtml(expiresAt ? expiresAt.substring(0, 16) : '24 Hours')}</strong>
                                    </div>
                                </div>

                                <div style="display: flex; gap: 8px; justify-content: flex-end;">
                                    <button type="button" class="btn btn-secondary btn-sm" style="color: var(--danger); border-color: var(--danger);"
                                            onclick="releaseAllocationAction(${res.reservationId}, '${escapeHtml(res.bookTitle || 'Book')}')">
                                        Release Allocation
                                    </button>
                                </div>
                            ` : `
                                <!-- Prominent Position Indicator (WAITING) -->
                                <div style="background-color: #F8F6F0; border-left: 3px solid var(--primary); padding: 8px 12px; border-radius: 4px; margin-bottom: 14px;">
                                    <div style="font-size: 0.85rem; color: var(--text-secondary);">Your Queue Position</div>
                                    <div style="font-size: 1.25rem; font-weight: bold; color: var(--primary);">
                                        Position #${pos} <span style="font-size: 0.85rem; font-weight: normal; color: var(--text-secondary);">of ${total} in FIFO line</span>
                                    </div>
                                </div>

                                <div style="display: flex; gap: 8px; justify-content: flex-end;">
                                    <button type="button" class="btn btn-secondary btn-sm" style="color: var(--danger); border-color: var(--danger);"
                                            onclick="cancelReservationAction(${res.reservationId}, '${escapeHtml(res.bookTitle || 'Book')}')">
                                        Cancel Reservation
                                    </button>
                                </div>
                            `}
                        </div>
                    </div>
                `;
            }
            html += '</div>';
            activeContainer.innerHTML = html;
        }

        // Render Reservation History (Table)
        if (historyList.length === 0) {
            historyTableBody.innerHTML = `
                <tr>
                    <td colspan="5" style="text-align: center; padding: 24px; color: var(--text-secondary);">
                        No historical reservations found.
                    </td>
                </tr>
            `;
        } else {
            let tableHtml = '';
            for (const h of historyList) {
                let badgeStyle = '';
                if (h.status === 'FULFILLED') {
                    badgeStyle = 'background-color: #E8F8F0; color: #159B50; border: 1px solid #159B50;';
                } else if (h.status === 'CANCELLED') {
                    badgeStyle = 'background-color: #FDE8E8; color: #C0392B; border: 1px solid #C0392B;';
                } else if (h.status === 'EXPIRED') {
                    badgeStyle = 'background-color: #FEF6E8; color: #C58B24; border: 1px solid #C58B24;';
                }

                tableHtml += `
                    <tr>
                        <td><strong>#${h.reservationId}</strong></td>
                        <td>
                            <a href="book-details.html?id=${h.bookId}" style="color: var(--primary); font-weight: bold; text-decoration: none;">
                                ${escapeHtml(h.bookTitle || 'Untitled Book')}
                            </a>
                            <div class="text-secondary" style="font-size: 0.85rem;">${escapeHtml(h.bookAuthor || '')}</div>
                        </td>
                        <td>${escapeHtml(h.reservationDate || '--')}</td>
                        <td>${escapeHtml(h.cancelledAt ? h.cancelledAt.substring(0, 10) : (h.updatedAt ? h.updatedAt.substring(0, 10) : '--'))}</td>
                        <td>
                            <span class="badge" style="${badgeStyle}">
                                ${escapeHtml(h.status)}
                            </span>
                        </td>
                    </tr>
                `;
            }
            historyTableBody.innerHTML = tableHtml;
        }

    } catch (e) {
        console.error('Error fetching reservations:', e);
        activeContainer.innerHTML = `<div class="info-card text-danger" style="padding: 20px;">Network or server error loading reservations.</div>`;
    }
}

/**
 * Student cancels a WAITING reservation.
 */
window.cancelReservationAction = async function (reservationId, bookTitle) {
    if (!confirm(`Are you sure you want to cancel your reservation for "${bookTitle}"?\n\nYou will lose your current FIFO queue position.`)) {
        return;
    }

    try {
        const resp = await fetch('reservations/cancel', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ reservationId: reservationId })
        });

        const result = await resp.json();
        if (resp.ok && result.success) {
            alert('Reservation cancelled successfully.');
            loadMyReservations();
        } else {
            alert('Failed to cancel reservation: ' + (result.message || 'Unknown error.'));
        }
    } catch (e) {
        console.error('Error cancelling reservation:', e);
        alert('Network error while cancelling reservation. Please try again.');
    }
};

/**
 * Student releases an ALLOCATED copy (Feature 5 Rule 5.8).
 */
window.releaseAllocationAction = async function (reservationId, bookTitle) {
    if (!confirm(`Are you sure you want to release your allocated copy of "${bookTitle}"?\n\nThe copy will be automatically offered to the next student in the queue, or returned to available circulation.`)) {
        return;
    }

    try {
        const resp = await fetch('reservations/cancel', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ reservationId: reservationId })
        });

        const result = await resp.json();
        if (resp.ok && result.success) {
            const outcome = result.data ? result.data.copyOutcome : null;
            if (outcome === 'REALLOCATED') {
                alert('Allocation released. The book copy has been assigned to the next eligible student waiting in the queue.');
            } else {
                alert('Allocation released. The book copy has been returned to the library shelves.');
            }
            loadMyReservations();
        } else {
            alert('Failed to release allocation: ' + (result.message || 'Unknown error.'));
        }
    } catch (e) {
        console.error('Error releasing allocation:', e);
        alert('Network error while releasing allocation. Please try again.');
    }
};
