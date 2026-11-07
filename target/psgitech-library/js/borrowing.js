/**
 * PSG iTech Library - Feature 3: Book Borrowing & Return Management Module
 * Handles Student Borrowings Views and Staff Circulation Desks (Issue, Return, Active, Overdue, History)
 */

document.addEventListener('DOMContentLoaded', function () {
    if (document.getElementById('studentBorrowingsContainer')) {
        initStudentBorrowingsPage();
    }
    if (document.getElementById('borrowingDeskContainer')) {
        initStaffBorrowingDesk();
    }
});

// =============================================================================
// Student Portal: My Borrowings
// =============================================================================

function initStudentBorrowingsPage() {
    loadStudentActiveBorrowings();
    loadStudentBorrowingHistory();
    loadStudentIncomingInterchanges();
    loadStudentOutgoingInterchanges();

    // Tab switcher
    window.switchStudentTab = function (tabId) {
        document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
        document.querySelectorAll('.tab-content').forEach(tab => tab.classList.remove('active'));

        const targetBtn = document.getElementById(tabId + 'Btn');
        const targetContent = document.getElementById(tabId);
        if (targetBtn) targetBtn.classList.add('active');
        if (targetContent) targetContent.classList.add('active');

        if (tabId === 'tabIncomingInterchanges') loadStudentIncomingInterchanges();
        if (tabId === 'tabOutgoingInterchanges') loadStudentOutgoingInterchanges();
        if (tabId === 'tabBorrowingHistory') loadStudentBorrowingHistory();
        if (tabId === 'tabCurrentlyBorrowed') loadStudentActiveBorrowings();
    };
}

async function loadStudentActiveBorrowings() {
    const container = document.getElementById('studentActiveList');
    const badgeCount = document.getElementById('activeCountBadge');
    if (!container) return;

    try {
        const resp = await fetch('borrow/student?type=active');
        const json = await resp.json();

        if (json.success && Array.isArray(json.data)) {
            const list = json.data;
            if (badgeCount) badgeCount.textContent = list.length;

            if (list.length === 0) {
                container.innerHTML = `
                    <div class="empty-state">
                        <svg class="empty-state-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                            <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path>
                            <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path>
                        </svg>
                        <h3 class="empty-state-title">No Currently Borrowed Books</h3>
                        <p class="empty-state-desc">You do not have any physical books checked out at this time. Browse our academic collection to find textbooks for your curriculum.</p>
                        <a href="browse.html" class="btn btn-primary btn-sm">Browse Catalog</a>
                    </div>
                `;
                return;
            }

            container.innerHTML = list.map(tx => renderStudentBorrowingCard(tx)).join('');
        }
    } catch (e) {
        console.error('Failed to load active borrowings', e);
        container.innerHTML = `<p class="text-secondary" style="color: var(--danger);">Failed to load borrowed books.</p>`;
    }
}

async function loadStudentBorrowingHistory() {
    const tbody = document.getElementById('studentHistoryTableBody');
    const badgeCount = document.getElementById('historyCountBadge');
    if (!tbody) return;

    try {
        const resp = await fetch('borrow/student?type=history');
        const json = await resp.json();

        if (json.success && Array.isArray(json.data)) {
            const list = json.data;
            if (badgeCount) badgeCount.textContent = list.length;

            if (list.length === 0) {
                tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; padding: 24px;" class="text-secondary">No historical borrowing records found.</td></tr>`;
                return;
            }

            tbody.innerHTML = list.map(tx => `
                <tr>
                    <td><strong>${escapeHtml(tx.bookTitle || 'Unknown Title')}</strong></td>
                    <td><code>${escapeHtml(tx.copyCode || '-')}</code></td>
                    <td>${escapeHtml(tx.issueDate || '-')}</td>
                    <td>${escapeHtml(tx.dueDate || '-')}</td>
                    <td>${tx.returnDate ? escapeHtml(tx.returnDate) : (tx.transferred ? `<span class="badge badge-warning">Transferred to ${escapeHtml(tx.transferRecipientName || 'Peer')}</span>` : '<span class="text-secondary">Active</span>')}</td>
                    <td>${renderStatusBadge(tx)}</td>
                </tr>
            `).join('');
        }
    } catch (e) {
        console.error('Failed to load borrowing history', e);
    }
}

function renderStudentBorrowingCard(tx) {
    const title = escapeHtml(tx.bookTitle || 'Textbook');
    const author = escapeHtml(tx.bookAuthor || 'PSG iTech');
    const copyCode = escapeHtml(tx.copyCode || '-');
    const issueDate = escapeHtml(tx.issueDate || '-');
    const dueDate = escapeHtml(tx.dueDate || '-');
    const coverUrl = tx.coverImageUrl || 'https://covers.openlibrary.org/b/isbn/9780262033848-L.jpg';

    // Due date display computation
    let dueStatusHtml = '';
    if (tx.overdue) {
        dueStatusHtml = `<span class="badge badge-overdue">Overdue by ${tx.overdueDays} day${tx.overdueDays > 1 ? 's' : ''}</span>`;
    } else if (tx.daysRemaining === 0) {
        dueStatusHtml = `<span class="badge badge-due-today">Due Today</span>`;
    } else {
        dueStatusHtml = `<span class="badge badge-available">${tx.daysRemaining} days remaining</span>`;
    }

    const transferBtnHtml = (!tx.overdue && tx.status === 'ACTIVE') ? `
        <button class="btn btn-secondary btn-sm" onclick="openTransferModal(${tx.transactionId}, '${escapeHtml(tx.bookTitle || '').replace(/'/g, "\\'")}', '${escapeHtml(tx.copyCode || '')}', '${escapeHtml(tx.issueDate || '')}', '${escapeHtml(tx.dueDate || '')}')">
            &rarr; Transfer Book to Peer
        </button>
    ` : '';

    const peerOriginHtml = tx.acquiredViaInterchange ? `
        <span class="badge badge-info" style="font-size: 0.8rem; background: #e0f2fe; color: #0369a1; border: 1px solid #bae6fd;">
            Acquired via Peer Transfer from ${escapeHtml(tx.originalBorrowerName || 'Peer')}
        </span>
    ` : '';

    return `
        <div class="borrowing-card" style="margin-bottom: 20px;">
            <div class="borrowing-card-cover">
                <img src="${escapeHtml(coverUrl)}" alt="${title}" style="width: 100%; height: 100%; object-fit: cover;"
                     onerror="this.src='https://covers.openlibrary.org/b/isbn/9780262033848-L.jpg'">
            </div>
            <div class="borrowing-card-info">
                <div>
                    <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 6px;">
                        <h3 class="borrowing-card-title">${title}</h3>
                        ${dueStatusHtml}
                    </div>
                    <div class="borrowing-card-meta">By ${author} &bull; Physical Copy: <code>${copyCode}</code></div>
                </div>

                <div class="borrowing-card-dates">
                    <div class="borrowing-date-item">
                        <span class="borrowing-date-label">Issue Date</span>
                        <span class="borrowing-date-val">${issueDate}</span>
                    </div>
                    <div class="borrowing-date-item">
                        <span class="borrowing-date-label">Due Date</span>
                        <span class="borrowing-date-val" style="color: ${tx.overdue ? 'var(--danger)' : 'var(--primary)'}; font-weight: bold;">${dueDate}</span>
                    </div>
                    <div class="borrowing-date-item">
                        <span class="borrowing-date-label">Standard Duration</span>
                        <span class="borrowing-date-val">14 Days (Fixed)</span>
                    </div>
                </div>

                <div style="margin-top: 14px; padding-top: 10px; border-top: 1px solid var(--border); display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px;">
                    ${peerOriginHtml}
                    ${transferBtnHtml}
                </div>
            </div>
        </div>
    `;
}

function renderStatusBadge(tx) {
    if (tx.transferred) {
        return `<span class="badge badge-warning" style="background: #fef3c7; color: #92400e;">Transferred</span>`;
    }
    if (tx.status === 'RETURNED') {
        return `<span class="badge badge-returned">Returned</span>`;
    }
    if (tx.overdue) {
        return `<span class="badge badge-overdue">Overdue (${tx.overdueDays}d)</span>`;
    }
    if (tx.daysRemaining === 0) {
        return `<span class="badge badge-due-today">Due Today</span>`;
    }
    if (tx.acquiredViaInterchange) {
        return `<span class="badge badge-active" style="background: #e0f2fe; color: #0369a1;">Active (Peer Transfer)</span>`;
    }
    return `<span class="badge badge-active">Active</span>`;
}

// =============================================================================
// Feature 8: Student-to-Student Book Interchange Client Logic
// =============================================================================

async function loadStudentIncomingInterchanges() {
    const container = document.getElementById('studentIncomingList');
    const badge = document.getElementById('incomingCountBadge');
    if (!container) return;

    try {
        const resp = await fetch('interchanges?type=incoming');
        const json = await resp.json();

        if (json.success && Array.isArray(json.data)) {
            const list = json.data;
            if (badge) badge.textContent = list.length;

            if (list.length === 0) {
                container.innerHTML = `
                    <div class="empty-state">
                        <p class="text-secondary">No pending incoming transfer requests from peers.</p>
                    </div>
                `;
                return;
            }

            container.innerHTML = list.map(req => `
                <div class="borrowing-card" style="margin-bottom: 16px; border-left: 4px solid var(--primary);">
                    <div class="borrowing-card-cover" style="width: 90px; height: 120px;">
                        <img src="${escapeHtml(req.coverImageUrl || 'https://covers.openlibrary.org/b/isbn/9780262033848-L.jpg')}" 
                             alt="${escapeHtml(req.bookTitle || 'Book')}" style="width: 100%; height: 100%; object-fit: cover;"
                             onerror="this.src='https://covers.openlibrary.org/b/isbn/9780262033848-L.jpg'">
                    </div>
                    <div class="borrowing-card-info" style="flex: 1;">
                        <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 8px;">
                            <div>
                                <h4 style="margin: 0 0 4px 0; color: var(--primary);">${escapeHtml(req.bookTitle || 'Textbook')}</h4>
                                <div class="text-secondary" style="font-size: 0.85rem;">
                                    From: <strong>${escapeHtml(req.fromStudentName || 'Student')}</strong> (${escapeHtml(req.fromStudentRegNo || '-')}) &bull; Copy: <code>${escapeHtml(req.copyCode || '-')}</code>
                                </div>
                            </div>
                            <span class="badge badge-info" style="background: #e0f2fe; color: #0369a1;">Pending Acceptance</span>
                        </div>

                        <div style="background: var(--bg-featured); border: 1px solid var(--border); border-radius: 4px; padding: 8px 12px; margin: 8px 0; font-size: 0.85rem;">
                            <strong>Return Deadline:</strong> <span style="color: var(--danger); font-weight: bold;">${escapeHtml(req.originalDueDate || '-')}</span>
                            &bull; <span>Original Issue: ${escapeHtml(req.originalIssueDate || '-')}</span>
                            ${req.requestNote ? `<div style="margin-top: 4px; color: var(--secondary);"><em>Note: "${escapeHtml(req.requestNote)}"</em></div>` : ''}
                        </div>

                        <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 10px; flex-wrap: wrap; gap: 8px;">
                            <small class="text-secondary">Requested: ${escapeHtml(req.requestedAt ? req.requestedAt.substring(0, 16) : '')}</small>
                            <div style="display: flex; gap: 8px;">
                                <button type="button" class="btn btn-outline btn-sm" onclick="openRejectModal(${req.interchangeId})">Decline</button>
                                <button type="button" class="btn btn-primary btn-sm" onclick="acceptBookInterchange(${req.interchangeId}, '${escapeHtml(req.bookTitle || '').replace(/'/g, "\\'")}', '${escapeHtml(req.originalDueDate || '')}')">
                                    Accept &amp; Confirm Physical Receipt
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            `).join('');
        }
    } catch (e) {
        console.error('Failed to load incoming interchanges', e);
        container.innerHTML = `<p class="text-secondary" style="color: var(--danger);">Failed to load incoming requests.</p>`;
    }
}

async function loadStudentOutgoingInterchanges() {
    const container = document.getElementById('studentOutgoingList');
    const badge = document.getElementById('outgoingCountBadge');
    if (!container) return;

    try {
        const resp = await fetch('interchanges?type=outgoing');
        const json = await resp.json();

        if (json.success && Array.isArray(json.data)) {
            const list = json.data;
            if (badge) badge.textContent = list.length;

            if (list.length === 0) {
                container.innerHTML = `
                    <div class="empty-state">
                        <p class="text-secondary">You have not initiated any book transfers.</p>
                    </div>
                `;
                return;
            }

            container.innerHTML = list.map(req => {
                let statusBadge = '';
                let actionBtn = '';

                if (req.status === 'REQUESTED') {
                    statusBadge = `<span class="badge badge-warning">Awaiting Recipient</span>`;
                    actionBtn = `<button class="btn btn-outline btn-sm" onclick="cancelBookInterchange(${req.interchangeId})">Cancel Request</button>`;
                } else if (req.status === 'COMPLETED') {
                    statusBadge = `<span class="badge badge-success" style="background: #dcfce7; color: #166534;">Completed</span>`;
                } else if (req.status === 'REJECTED') {
                    statusBadge = `<span class="badge badge-danger" style="background: #fee2e2; color: #991b1b;">Declined</span>`;
                } else {
                    statusBadge = `<span class="badge badge-returned">Cancelled</span>`;
                }

                return `
                    <div class="borrowing-card" style="margin-bottom: 14px;">
                        <div class="borrowing-card-info" style="width: 100%;">
                            <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 8px;">
                                <div>
                                    <h4 style="margin: 0 0 4px 0;">${escapeHtml(req.bookTitle || 'Book')}</h4>
                                    <div class="text-secondary" style="font-size: 0.85rem;">
                                        To: <strong>${escapeHtml(req.toStudentName || 'Student')}</strong> (${escapeHtml(req.toStudentRegNo || '-')}) &bull; Copy: <code>${escapeHtml(req.copyCode || '-')}</code>
                                    </div>
                                </div>
                                ${statusBadge}
                            </div>

                            <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 10px; font-size: 0.85rem; color: var(--secondary);">
                                <div>
                                    Due Date: <strong>${escapeHtml(req.originalDueDate || '-')}</strong> &bull;
                                    Requested: ${escapeHtml(req.requestedAt ? req.requestedAt.substring(0, 16) : '')}
                                    ${req.rejectionReason ? `<div style="color: var(--danger); margin-top: 4px;">Declined Reason: "${escapeHtml(req.rejectionReason)}"</div>` : ''}
                                    ${req.cancellationReason ? `<div style="color: var(--secondary); margin-top: 4px;">Cancellation: "${escapeHtml(req.cancellationReason)}"</div>` : ''}
                                </div>
                                <div>${actionBtn}</div>
                            </div>
                        </div>
                    </div>
                `;
            }).join('');
        }
    } catch (e) {
        console.error('Failed to load outgoing interchanges', e);
        container.innerHTML = `<p class="text-secondary" style="color: var(--danger);">Failed to load outgoing requests.</p>`;
    }
}

// Modal Handlers

window.openTransferModal = function (txId, title, copyCode, issueDate, dueDate) {
    document.getElementById('modalTransferTxId').value = txId;
    document.getElementById('modalTransferTitle').textContent = title;
    document.getElementById('modalTransferCopy').textContent = copyCode;
    document.getElementById('modalTransferIssue').textContent = issueDate;
    document.getElementById('modalTransferDue').textContent = dueDate;
    document.getElementById('modalTransferRegNo').value = '';
    document.getElementById('modalTransferNote').value = '';

    const modal = document.getElementById('transferBookModal');
    if (modal) modal.classList.add('show');
};

window.closeTransferModal = function () {
    const modal = document.getElementById('transferBookModal');
    if (modal) modal.classList.remove('show');
};

window.openRejectModal = function (interchangeId) {
    document.getElementById('modalRejectId').value = interchangeId;
    document.getElementById('modalRejectReason').value = '';
    const modal = document.getElementById('rejectInterchangeModal');
    if (modal) modal.classList.add('show');
};

window.closeRejectModal = function () {
    const modal = document.getElementById('rejectInterchangeModal');
    if (modal) modal.classList.remove('show');
};

window.submitBookTransfer = async function () {
    const txId = document.getElementById('modalTransferTxId').value;
    const regNo = document.getElementById('modalTransferRegNo').value.trim();
    const note = document.getElementById('modalTransferNote').value.trim();
    const btn = document.getElementById('btnConfirmSendTransfer');

    if (!regNo) {
        alert('Please enter the recipient student register number.');
        return;
    }

    if (btn) btn.disabled = true;

    try {
        const resp = await fetch('interchanges', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest'
            },
            body: JSON.stringify({
                transactionId: parseInt(txId, 10),
                recipientRegisterNumber: regNo,
                requestNote: note
            })
        });

        const json = await resp.json();
        if (resp.ok && json.success) {
            alert(json.message || 'Interchange request sent successfully!');
            closeTransferModal();
            loadStudentActiveBorrowings();
            loadStudentOutgoingInterchanges();
            switchStudentTab('tabOutgoingInterchanges');
        } else {
            alert('Transfer Request Failed: ' + (json.message || 'Unknown server error'));
        }
    } catch (e) {
        console.error('Failed to submit transfer request', e);
        alert('Network or server error while initiating transfer.');
    } finally {
        if (btn) btn.disabled = false;
    }
};

window.acceptBookInterchange = async function (interchangeId, bookTitle, dueDate) {
    const confirmed = confirm(
        `Confirm Physical Handover Receipt:\n\n` +
        `Book: "${bookTitle}"\n` +
        `Return Deadline: ${dueDate} (Strictly unchanged)\n\n` +
        `By clicking OK, you confirm that you have physically received this textbook and agree to return it by ${dueDate}.`
    );

    if (!confirmed) return;

    try {
        const resp = await fetch('interchanges/accept', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest'
            },
            body: JSON.stringify({ interchangeId: interchangeId })
        });

        const json = await resp.json();
        if (resp.ok && json.success) {
            alert(json.message || 'Interchange completed! The book is now under your borrowings.');
            loadStudentActiveBorrowings();
            loadStudentIncomingInterchanges();
            switchStudentTab('tabCurrentlyBorrowed');
        } else {
            alert('Interchange Acceptance Failed: ' + (json.message || 'Conflict or eligibility violation'));
        }
    } catch (e) {
        console.error('Error accepting interchange', e);
        alert('Network or server error accepting transfer.');
    }
};

window.submitRejectInterchange = async function () {
    const interchangeId = document.getElementById('modalRejectId').value;
    const reason = document.getElementById('modalRejectReason').value.trim();
    const btn = document.getElementById('btnConfirmRejectTransfer');

    if (btn) btn.disabled = true;

    try {
        const resp = await fetch('interchanges/reject', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest'
            },
            body: JSON.stringify({
                interchangeId: parseInt(interchangeId, 10),
                reason: reason
            })
        });

        const json = await resp.json();
        if (resp.ok && json.success) {
            alert(json.message || 'Request declined.');
            closeRejectModal();
            loadStudentIncomingInterchanges();
        } else {
            alert('Failed to decline: ' + (json.message || 'Unknown error'));
        }
    } catch (e) {
        console.error('Error rejecting interchange', e);
        alert('Network error declining request.');
    } finally {
        if (btn) btn.disabled = false;
    }
};

window.cancelBookInterchange = async function (interchangeId) {
    if (!confirm('Are you sure you want to cancel this outgoing transfer request?')) return;

    try {
        const resp = await fetch('interchanges/cancel', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest'
            },
            body: JSON.stringify({
                interchangeId: interchangeId,
                reason: 'Cancelled by sender'
            })
        });

        const json = await resp.json();
        if (resp.ok && json.success) {
            alert(json.message || 'Transfer request cancelled.');
            loadStudentOutgoingInterchanges();
        } else {
            alert('Failed to cancel request: ' + (json.message || 'Conflict detected'));
        }
    } catch (e) {
        console.error('Error cancelling interchange', e);
        alert('Network error cancelling request.');
    }
};

// =============================================================================
// Staff Borrowing Management Desk (Librarian & Admin)
// =============================================================================

let currentEligibleStudent = null;
let currentSelectedBook = null;

function initStaffBorrowingDesk() {
    initTabs();
    initIssueDesk();
    initActiveBorrowingsTable();
    initReturnDesk();
    initHistoryTable();
    initOverdueTable();
    loadAllQueueReservations();
    loadHoldShelfAllocations();
    loadPendingVerifications();
}

function initTabs() {
    window.switchDeskTab = function (tabId) {
        document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.desk-tab-content').forEach(c => c.classList.remove('active'));

        const btn = document.getElementById(tabId + 'Btn');
        const content = document.getElementById(tabId);
        if (btn) btn.classList.add('active');
        if (content) content.classList.add('active');

        if (tabId === 'tabActive') loadAllActiveBorrowings();
        if (tabId === 'tabHistory') loadAllHistory();
        if (tabId === 'tabOverdue') loadAllOverdue();
        if (tabId === 'tabQueue') loadAllQueueReservations();
        if (tabId === 'tabHoldShelf') loadHoldShelfAllocations();
        if (tabId === 'tabFines') loadStaffFines();
        if (tabId === 'tabVerif') loadPendingVerifications();
    };
}

// -----------------------------------------------------------------------------
// Issue Desk Logic
// -----------------------------------------------------------------------------
function initIssueDesk() {
    const regInput = document.getElementById('issueStudentRegNo');
    const bookSearchInput = document.getElementById('issueBookSearch');
    const issueBtn = document.getElementById('btnConfirmIssue');

    if (regInput) {
        regInput.addEventListener('change', () => lookupStudentEligibility());
        regInput.addEventListener('keyup', debounce(() => lookupStudentEligibility(), 500));
    }

    if (bookSearchInput) {
        bookSearchInput.addEventListener('keyup', debounce(() => searchBooksForIssue(), 300));
        bookSearchInput.addEventListener('focus', () => searchBooksForIssue());
    }

    if (issueBtn) {
        issueBtn.addEventListener('click', handleConfirmIssue);
    }

    // Set today and auto 14 days due date display
    updateIssueDateDisplays();
}

function updateIssueDateDisplays() {
    const today = new Date();
    const dueDate = new Date();
    dueDate.setDate(today.getDate() + 14);

    const fmt = d => d.toISOString().split('T')[0];
    const issueDateEl = document.getElementById('issueDateDisplay');
    const dueDateEl = document.getElementById('dueDateDisplay');

    if (issueDateEl) issueDateEl.textContent = fmt(today);
    if (dueDateEl) dueDateEl.textContent = fmt(dueDate);
}

async function lookupStudentEligibility() {
    const regInput = document.getElementById('issueStudentRegNo');
    const panel = document.getElementById('studentInfoPanel');
    const msgBox = document.getElementById('studentLookupMsg');
    if (!regInput || !panel) return;

    const regNo = regInput.value.trim();
    if (!regNo) {
        panel.style.display = 'none';
        currentEligibleStudent = null;
        updateIssueButtonState();
        return;
    }

    try {
        let url = `borrow/lookup?type=student&registerNumber=${encodeURIComponent(regNo)}`;
        if (currentSelectedBook && currentSelectedBook.bookId) {
            url += `&bookId=${currentSelectedBook.bookId}`;
        }

        const resp = await fetch(url);
        const json = await resp.json();

        if (json.success && json.data) {
            const s = json.data;
            currentEligibleStudent = s;

            panel.style.display = 'block';
            document.getElementById('infoStudentName').textContent = s.studentName;
            document.getElementById('infoStudentDeptYear').textContent = `${s.department || '-'}, Year ${s.year || '-'}`;
            document.getElementById('infoStudentStatus').textContent = s.accountStatus;
            document.getElementById('infoStudentStatus').className = s.accountStatus === 'ACTIVE' ? 'badge badge-active' : 'badge badge-inactive';
            document.getElementById('infoActiveCount').textContent = s.activeBorrowings;
            document.getElementById('infoRemainingQuota').textContent = s.remainingCapacity;

            // Eligibility warnings
            if (!s.isEligible) {
                if (s.accountStatus !== 'ACTIVE') {
                    showStudentWarning('Student account is suspended or inactive. Cannot issue books.', true);
                } else if (s.remainingCapacity <= 0) {
                    showStudentWarning(`Student has reached maximum active borrowing quota (${s.maxLimit} books).`, true);
                }
            } else if (s.alreadyHasSameBook) {
                showStudentWarning(`Student already has an active copy of this book. Duplicate copy checkout is prohibited.`, true);
            } else {
                showStudentWarning('Student is verified and eligible for book issuance.', false);
            }
        } else {
            panel.style.display = 'none';
            currentEligibleStudent = null;
            if (msgBox) {
                msgBox.textContent = json.message || 'Student not found.';
                msgBox.style.display = 'block';
                msgBox.className = 'alert alert-danger';
            }
        }
    } catch (e) {
        console.error('Error looking up student', e);
    }
    updateIssueButtonState();
}

function showStudentWarning(text, isError) {
    const msgBox = document.getElementById('studentLookupMsg');
    if (!msgBox) return;
    msgBox.textContent = text;
    msgBox.style.display = 'block';
    msgBox.style.backgroundColor = isError ? '#FDE8E8' : '#E8F8F0';
    msgBox.style.color = isError ? '#C0392B' : '#159B50';
    msgBox.style.border = isError ? '1px solid #F8B4B4' : '1px solid #B8E8D0';
}

async function searchBooksForIssue() {
    const input = document.getElementById('issueBookSearch');
    const dropdown = document.getElementById('issueBookResults');
    if (!input || !dropdown) return;

    const q = input.value.trim();
    if (q.length < 1) {
        dropdown.style.display = 'none';
        return;
    }

    try {
        const resp = await fetch(`books/search?q=${encodeURIComponent(q)}`);
        const json = await resp.json();

        if (json.success && Array.isArray(json.data) && json.data.length > 0) {
            dropdown.innerHTML = json.data.map(b => {
                const avail = (b.inventory && b.inventory.availableCopies) || 0;
                return `
                    <div class="desk-search-item" onclick="selectBookForIssue(${b.bookId})">
                        <strong>${escapeHtml(b.title)}</strong> by ${escapeHtml(b.author)}
                        <div class="text-secondary" style="font-size: 0.85rem; margin-top: 2px;">
                            ISBN: ${escapeHtml(b.isbn)} &bull; ${avail > 0 ? `<span style="color: var(--success); font-weight: bold;">${avail} Available</span>` : `<span style="color: var(--danger);">No copies available</span>`}
                        </div>
                    </div>
                `;
            }).join('');
            dropdown.style.display = 'block';
        } else {
            dropdown.innerHTML = `<div class="desk-search-item text-secondary">No matching active books found.</div>`;
            dropdown.style.display = 'block';
        }
    } catch (e) {
        console.error('Error searching books for issue', e);
    }
}

async function selectBookForIssue(bookId) {
    const dropdown = document.getElementById('issueBookResults');
    const searchInput = document.getElementById('issueBookSearch');
    const selectedPanel = document.getElementById('selectedBookPanel');
    const copySelect = document.getElementById('issueCopySelect');
    if (dropdown) dropdown.style.display = 'none';

    try {
        const resp = await fetch(`books/details?bookId=${bookId}&includeCopies=true`);
        const json = await resp.json();

        if (json.success && json.data) {
            const b = json.data;
            currentSelectedBook = b;
            if (searchInput) searchInput.value = b.title;

            if (selectedPanel) {
                selectedPanel.style.display = 'block';
                document.getElementById('selBookTitle').textContent = b.title;
                document.getElementById('selBookAuthor').textContent = b.author;
                document.getElementById('selBookIsbn').textContent = b.isbn;
                document.getElementById('selBookDept').textContent = b.department;
            }

            // Populate available physical copies
            const copies = (b.inventory && b.inventory.copies) || [];
            const availableCopies = copies.filter(c => c.status === 'AVAILABLE');

            if (copySelect) {
                if (availableCopies.length === 0) {
                    copySelect.innerHTML = `<option value="">-- No AVAILABLE copies on shelf --</option>`;
                } else {
                    copySelect.innerHTML = `<option value="">-- Select Available Copy (${availableCopies.length}) --</option>` +
                        availableCopies.map(c => `<option value="${c.copyId}">${escapeHtml(c.copyCode)} (Shelf Available)</option>`).join('');
                }
            }

            // Check if current student already has this book
            if (currentEligibleStudent) {
                lookupStudentEligibility();
            }
        }
    } catch (e) {
        console.error('Error fetching book details for issue', e);
    }
    updateIssueButtonState();
}

function updateIssueButtonState() {
    const btn = document.getElementById('btnConfirmIssue');
    const copySelect = document.getElementById('issueCopySelect');
    if (!btn) return;

    const hasStudent = currentEligibleStudent && currentEligibleStudent.isEligible && !currentEligibleStudent.alreadyHasSameBook;
    const hasCopy = copySelect && copySelect.value && copySelect.value !== '';

    btn.disabled = !(hasStudent && hasCopy);
}

// Handle physical copy dropdown change
document.addEventListener('change', function (e) {
    if (e.target && e.target.id === 'issueCopySelect') {
        updateIssueButtonState();
    }
});

async function handleConfirmIssue() {
    const regInput = document.getElementById('issueStudentRegNo');
    const copySelect = document.getElementById('issueCopySelect');
    const alertBox = document.getElementById('issueAlertBox');
    const btn = document.getElementById('btnConfirmIssue');

    if (!regInput || !copySelect) return;

    const regNo = regInput.value.trim();
    const copyId = copySelect.value;

    if (!regNo || !copyId) {
        alert('Please select both a valid student and an available physical copy.');
        return;
    }

    btn.disabled = true;
    btn.textContent = 'Processing Issue...';

    try {
        const resp = await fetch('borrow/issue', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                registerNumber: regNo,
                copyId: parseInt(copyId)
            })
        });

        const json = await resp.json();

        if (json.success) {
            if (alertBox) {
                alertBox.style.display = 'block';
                alertBox.className = 'alert alert-success';
                alertBox.innerHTML = `<strong>Success!</strong> ${escapeHtml(json.message)}. 
                    Transaction #${json.data.transactionId} created for copy <code>${escapeHtml(json.data.copyCode)}</code>. 
                    Due date: <strong>${json.data.dueDate}</strong>.`;
            }

            // Reset form
            regInput.value = '';
            document.getElementById('issueBookSearch').value = '';
            document.getElementById('selectedBookPanel').style.display = 'none';
            document.getElementById('studentInfoPanel').style.display = 'none';
            document.getElementById('studentLookupMsg').style.display = 'none';
            currentEligibleStudent = null;
            currentSelectedBook = null;

            // Reload active tables
            loadAllActiveBorrowings();

        } else {
            if (alertBox) {
                alertBox.style.display = 'block';
                alertBox.className = 'alert alert-danger';
                alertBox.innerHTML = `<strong>Issue Rejected:</strong> ${escapeHtml(json.message)}`;
            }
        }
    } catch (e) {
        console.error('Error during issue', e);
        if (alertBox) {
            alertBox.style.display = 'block';
            alertBox.className = 'alert alert-danger';
            alertBox.textContent = 'Unexpected server error occurred during book issuance.';
        }
    } finally {
        btn.disabled = false;
        btn.textContent = 'Confirm Issue & Checkout';
        updateIssueButtonState();
    }
}

// -----------------------------------------------------------------------------
// Active Borrowings Table
// -----------------------------------------------------------------------------
async function loadAllActiveBorrowings() {
    const tbody = document.getElementById('activeBorrowingsTableBody');
    const searchInput = document.getElementById('activeSearchInput');
    const countBadge = document.getElementById('activeTabCountBadge');
    if (!tbody) return;

    let url = 'borrow/active';
    if (searchInput && searchInput.value.trim()) {
        url += `?q=${encodeURIComponent(searchInput.value.trim())}`;
    }

    try {
        const resp = await fetch(url);
        const json = await resp.json();

        if (json.success && Array.isArray(json.data)) {
            const list = json.data;
            if (countBadge) countBadge.textContent = list.length;

            if (list.length === 0) {
                tbody.innerHTML = `<tr><td colspan="9" style="text-align: center; padding: 24px;" class="text-secondary">No active borrowing records found.</td></tr>`;
                return;
            }

            tbody.innerHTML = list.map(tx => `
                <tr>
                    <td><code>#${tx.transactionId}</code></td>
                    <td><strong>${escapeHtml(tx.studentName || '-')}</strong></td>
                    <td><code>${escapeHtml(tx.registerNumber || '-')}</code></td>
                    <td>${escapeHtml(tx.bookTitle || '-')}</td>
                    <td><code>${escapeHtml(tx.copyCode || '-')}</code></td>
                    <td>${escapeHtml(tx.issueDate || '-')}</td>
                    <td><span style="font-weight: bold; color: ${tx.overdue ? 'var(--danger)' : 'var(--primary)'};">${escapeHtml(tx.dueDate || '-')}</span></td>
                    <td>${renderStatusBadge(tx)}</td>
                    <td>
                        <button class="btn btn-secondary btn-sm" onclick="initiateReturnFromRow(${tx.transactionId}, '${escapeJs(tx.bookTitle)}', '${escapeJs(tx.copyCode)}', '${escapeJs(tx.studentName)}')">Return</button>
                    </td>
                </tr>
            `).join('');
        }
    } catch (e) {
        console.error('Error loading active borrowings', e);
    }
}

// -----------------------------------------------------------------------------
// Return Desk Logic
// -----------------------------------------------------------------------------
function initReturnDesk() {
    const searchBtn = document.getElementById('btnSearchReturn');
    const searchInput = document.getElementById('returnSearchInput');
    const confirmReturnBtn = document.getElementById('btnConfirmReturn');

    if (searchBtn && searchInput) {
        searchBtn.addEventListener('click', () => searchReturnTransaction());
        searchInput.addEventListener('keyup', (e) => {
            if (e.key === 'Enter') searchReturnTransaction();
        });
    }

    if (confirmReturnBtn) {
        confirmReturnBtn.addEventListener('click', handleConfirmReturn);
    }
}

let activeReturnCandidate = null;

async function searchReturnTransaction() {
    const input = document.getElementById('returnSearchInput');
    const card = document.getElementById('returnCandidateCard');
    const msg = document.getElementById('returnDeskMsg');
    if (!input) return;

    const q = input.value.trim();
    if (!q) {
        alert('Please enter a Copy Code, Transaction ID, or Student Register Number.');
        return;
    }

    try {
        const resp = await fetch(`borrow/lookup?type=return&q=${encodeURIComponent(q)}`);
        const json = await resp.json();

        if (json.success && Array.isArray(json.data) && json.data.length > 0) {
            const tx = json.data[0]; // best match
            activeReturnCandidate = tx;

            if (msg) msg.style.display = 'none';
            if (card) {
                card.style.display = 'block';
                document.getElementById('retCandidateTxId').textContent = '#' + tx.transactionId;
                document.getElementById('retCandidateTitle').textContent = tx.bookTitle;
                document.getElementById('retCandidateCopyCode').textContent = tx.copyCode;
                document.getElementById('retCandidateStudent').textContent = `${tx.studentName} (${tx.registerNumber})`;
                document.getElementById('retCandidateDates').textContent = `Issued: ${tx.issueDate} | Due: ${tx.dueDate}`;

                const overdueEl = document.getElementById('retCandidateOverdue');
                if (tx.overdue) {
                    overdueEl.innerHTML = `<span class="badge badge-overdue">Overdue by ${tx.overdueDays} days</span>`;
                } else {
                    overdueEl.innerHTML = `<span class="badge badge-available">On Time (${tx.daysRemaining} days remaining)</span>`;
                }
            }
        } else {
            activeReturnCandidate = null;
            if (card) card.style.display = 'none';
            if (msg) {
                msg.style.display = 'block';
                msg.className = 'alert alert-danger';
                msg.textContent = 'No matching active borrowing transaction found for: ' + q;
            }
        }
    } catch (e) {
        console.error('Error looking up return transaction', e);
    }
}

function initiateReturnFromRow(txId, title, copyCode, studentName) {
    window.switchDeskTab('tabReturn');
    const input = document.getElementById('returnSearchInput');
    if (input) {
        input.value = copyCode || String(txId);
        searchReturnTransaction();
    }
}

async function handleConfirmReturn() {
    if (!activeReturnCandidate || !activeReturnCandidate.transactionId) {
        alert('No active transaction selected for return.');
        return;
    }

    const btn = document.getElementById('btnConfirmReturn');
    const msg = document.getElementById('returnDeskMsg');
    const card = document.getElementById('returnCandidateCard');

    btn.disabled = true;
    btn.textContent = 'Recording Return...';

    try {
        const resp = await fetch('borrow/return', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ transactionId: activeReturnCandidate.transactionId })
        });

        const json = await resp.json();

        if (json.success) {
            if (card) card.style.display = 'none';
            if (msg) {
                msg.style.display = 'block';
                const alloc = json.data ? json.data.allocation : null;
                if (alloc) {
                    msg.className = 'alert';
                    msg.style.borderLeft = '4px solid #159B50';
                    msg.style.backgroundColor = '#E8F8F0';
                    msg.style.color = '#222222';
                    msg.innerHTML = `
                        <div style="font-size: 1.05rem; font-weight: bold; color: #159B50; margin-bottom: 6px;">
                            ✓ Return Processed &bull; Automatically Allocated to Reserved Student!
                        </div>
                        <div style="font-size: 0.95rem; line-height: 1.6;">
                            Copy <code>${escapeHtml(alloc.copyIdentifier || alloc.copyCode || json.data.copyCode || '')}</code> of 
                            <strong>${escapeHtml(alloc.bookTitle || 'Book')}</strong> is now <span class="badge" style="background:#FEF3C7; color:#B45309; font-weight:bold;">ALLOCATED</span> to:
                            <br>
                            Student: <strong>${escapeHtml(alloc.studentName || '')}</strong> (Reg No: <strong>${escapeHtml(alloc.registerNumber || '')}</strong>).
                            <br>
                            <span style="color: #159B50; font-weight: bold;">Desk Action:</span> <strong>Place this copy on the Hold Shelf for pickup.</strong> Collect deadline: ${escapeHtml(alloc.expiresAt ? alloc.expiresAt.substring(0, 16) : '24 Hours')}.
                        </div>
                    `;
                } else {
                    msg.className = 'alert alert-success';
                    msg.style.borderLeft = '4px solid #159B50';
                    msg.style.backgroundColor = '#E8F8F0';
                    msg.innerHTML = `<strong>Return Completed!</strong> ${escapeHtml(json.message)}. 
                        Copy <code>${escapeHtml(json.data.copyCode || '')}</code> returned to the shelf as <strong>AVAILABLE</strong>.`;
                }

                const fine = json.data ? json.data.fine : null;
                if (fine) {
                    msg.innerHTML += `
                        <div style="margin-top: 12px; padding: 12px 16px; background: #FDEDEC; border: 1px solid #E6B0AA; border-radius: 4px; color: #78281F;">
                            <strong style="color: #922B21;">Overdue Return &bull; Fine Incurred!</strong><br>
                            This physical copy was returned <strong>${fine.overdueDays} day${fine.overdueDays === 1 ? '' : 's'} late</strong>.
                            Atomic fine created: <strong>&inr; ${parseFloat(fine.amount || fine.outstanding).toFixed(2)}</strong> (Fine Record #${fine.fineId}).
                            <br>
                            <span style="font-size: 0.88rem;">Student's borrowing quota and reservation collections are temporarily paused until cleared via UPI or Admin waiver.</span>
                        </div>
                    `;
                }
            }
            activeReturnCandidate = null;
            document.getElementById('returnSearchInput').value = '';

            // Reload active tables, hold shelf, and fines
            loadAllActiveBorrowings();
            loadHoldShelfAllocations();
            loadAllQueueReservations();
            loadStaffFines();
            loadPendingVerifications();

        } else {
            if (msg) {
                msg.style.display = 'block';
                msg.className = 'alert alert-danger';
                msg.textContent = json.message || 'Failed to process book return.';
            }
        }
    } catch (e) {
        console.error('Error returning book', e);
    } finally {
        btn.disabled = false;
        btn.textContent = 'Confirm Book Return';
    }
}

// -----------------------------------------------------------------------------
// History Table
// -----------------------------------------------------------------------------
async function loadAllHistory() {
    const tbody = document.getElementById('allHistoryTableBody');
    const searchInput = document.getElementById('historySearchInput');
    if (!tbody) return;

    let url = 'borrow/history';
    if (searchInput && searchInput.value.trim()) {
        url += `?q=${encodeURIComponent(searchInput.value.trim())}`;
    }

    try {
        const resp = await fetch(url);
        const json = await resp.json();

        if (json.success && Array.isArray(json.data)) {
            const list = json.data;
            if (list.length === 0) {
                tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; padding: 24px;" class="text-secondary">No borrowing history found.</td></tr>`;
                return;
            }

            tbody.innerHTML = list.map(tx => `
                <tr>
                    <td><code>#${tx.transactionId}</code></td>
                    <td><strong>${escapeHtml(tx.studentName || '-')}</strong> (${escapeHtml(tx.registerNumber || '-')})</td>
                    <td>${escapeHtml(tx.bookTitle || '-')}</td>
                    <td><code>${escapeHtml(tx.copyCode || '-')}</code></td>
                    <td>${escapeHtml(tx.issueDate || '-')}</td>
                    <td>${escapeHtml(tx.dueDate || '-')}</td>
                    <td>${tx.returnDate ? escapeHtml(tx.returnDate) : '<span class="text-secondary">Active</span>'}</td>
                    <td>${renderStatusBadge(tx)}</td>
                </tr>
            `).join('');
        }
    } catch (e) {
        console.error('Error loading history', e);
    }
}

// -----------------------------------------------------------------------------
// Overdue Records Table
// -----------------------------------------------------------------------------
async function loadAllOverdue() {
    const tbody = document.getElementById('overdueTableBody');
    const countBadge = document.getElementById('overdueTabCountBadge');
    if (!tbody) return;

    try {
        const resp = await fetch('borrow/overdue');
        const json = await resp.json();

        if (json.success && Array.isArray(json.data)) {
            const list = json.data;
            if (countBadge) countBadge.textContent = list.length;

            if (list.length === 0) {
                tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; padding: 24px; color: var(--success); font-weight: bold;">Excellent! There are no overdue book borrowings at this time.</td></tr>`;
                return;
            }

            tbody.innerHTML = list.map(tx => `
                <tr>
                    <td><code>#${tx.transactionId}</code></td>
                    <td><strong>${escapeHtml(tx.studentName || '-')}</strong></td>
                    <td><code>${escapeHtml(tx.registerNumber || '-')}</code></td>
                    <td>${escapeHtml(tx.bookTitle || '-')}</td>
                    <td><code>${escapeHtml(tx.copyCode || '-')}</code></td>
                    <td>${escapeHtml(tx.dueDate || '-')}</td>
                    <td><span class="badge badge-overdue">${tx.overdueDays} Days Overdue</span></td>
                    <td>
                        <button class="btn btn-secondary btn-sm" onclick="initiateReturnFromRow(${tx.transactionId}, '${escapeJs(tx.bookTitle)}', '${escapeJs(tx.copyCode)}', '${escapeJs(tx.studentName)}')">Process Return</button>
                    </td>
                </tr>
            `).join('');
        }
    } catch (e) {
        console.error('Error loading overdue records', e);
    }
}

// =============================================================================
// Helper Utilities
// =============================================================================
function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function escapeJs(str) {
    if (str === null || str === undefined) return '';
    return String(str)
        .replace(/\\/g, '\\\\')
        .replace(/'/g, "\\'")
        .replace(/"/g, '\\"')
        .replace(/\n/g, '\\n')
        .replace(/\r/g, '');
}

function debounce(func, wait) {
    let timeout;
    return function (...args) {
        clearTimeout(timeout);
        timeout = setTimeout(() => func.apply(this, args), wait);
    };
}

// =============================================================================
// Staff Circulation Desk: Feature 4 Reservation Queues Tab
// =============================================================================

async function loadAllQueueReservations() {
    const tbody = document.getElementById('allQueueTableBody');
    const badge = document.getElementById('queueTabCountBadge');
    const filterInput = document.getElementById('queueSearchInput');
    const filterText = (filterInput && filterInput.value) ? filterInput.value.trim().toLowerCase() : '';

    if (!tbody) return;

    try {
        const resp = await fetch('reservations?page=1&pageSize=100');
        const json = await resp.json();

        if (json.success && Array.isArray(json.reservations)) {
            let list = json.reservations;
            if (badge) badge.textContent = list.length;

            if (filterText) {
                list = list.filter(r => 
                    (r.bookTitle && r.bookTitle.toLowerCase().includes(filterText)) ||
                    (r.studentName && r.studentName.toLowerCase().includes(filterText)) ||
                    (r.registerNumber && r.registerNumber.toLowerCase().includes(filterText))
                );
            }

            if (list.length === 0) {
                tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; padding: 28px;" class="text-secondary">No active reservations in queue.</td></tr>`;
                return;
            }

            tbody.innerHTML = list.map(r => `
                <tr>
                    <td><strong style="color: var(--primary);">#${r.queuePosition || 1}</strong></td>
                    <td><strong>${escapeHtml(r.studentName || 'Student')}</strong></td>
                    <td><span style="font-family: monospace;">${escapeHtml(r.registerNumber || '--')}</span></td>
                    <td>
                        <a href="book-details.html?id=${r.bookId}" style="color: var(--primary); font-weight: bold; text-decoration: none;">
                            ${escapeHtml(r.bookTitle || 'Book')}
                        </a>
                    </td>
                    <td>${escapeHtml(r.reservationDate || '--')}</td>
                    <td>
                        <span class="badge" style="background-color: #FEF3C7; color: #B45309; border: 1px solid #FDE68A;">
                            ${escapeHtml(r.status || 'WAITING')}
                        </span>
                    </td>
                    <td style="text-align: right;">
                        <button class="btn btn-secondary btn-sm" style="color: var(--danger); border-color: var(--danger); padding: 4px 8px; font-size: 0.82rem;"
                                onclick="staffCancelReservation(${r.reservationId}, '${escapeJs(r.studentName || 'Student')}', '${escapeJs(r.bookTitle || 'Book')}')">
                            Cancel
                        </button>
                    </td>
                </tr>
            `).join('');
        } else {
            tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--danger); padding: 20px;">Failed to load reservations.</td></tr>`;
        }
    } catch (e) {
        console.error('Error loading reservation queues:', e);
        tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--danger); padding: 20px;">Network error loading reservations.</td></tr>`;
    }
}

window.staffCancelReservation = async function (reservationId, studentName, bookTitle) {
    if (!confirm(`Cancel reservation #${reservationId} for ${studentName} on "${bookTitle}"?`)) {
        return;
    }

    try {
        const resp = await fetch('reservations/cancel', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ reservationId: reservationId })
        });
        const res = await resp.json();
        if (resp.ok && res.success) {
            alert('Reservation cancelled successfully.');
            loadAllQueueReservations();
        } else {
            alert('Error cancelling reservation: ' + (res.message || 'Unknown error.'));
        }
    } catch (e) {
        alert('Network error while cancelling reservation.');
    }
};

// -----------------------------------------------------------------------------
// Feature 5: Hold Shelf / Allocations Management
// -----------------------------------------------------------------------------
async function loadHoldShelfAllocations() {
    const tbody = document.getElementById('holdShelfTableBody');
    const badge = document.getElementById('holdShelfTabCountBadge');
    const statusSelect = document.getElementById('holdShelfStatusFilter');
    if (!tbody) return;

    const status = statusSelect ? statusSelect.value : 'ALLOCATED';

    try {
        const resp = await fetch('allocations?status=' + encodeURIComponent(status));
        if (resp.status === 401) return;
        const json = await resp.json();

        if (json.success && json.data) {
            const list = json.data.allocations || [];
            if (badge && status === 'ALLOCATED') {
                badge.textContent = list.length;
            }

            if (list.length === 0) {
                tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; padding: 24px;" class="text-secondary">No ${escapeHtml(status)} allocations found on the hold shelf.</td></tr>`;
                return;
            }

            let html = '';
            for (const a of list) {
                const isAlloc = a.status === 'ALLOCATED';
                html += `
                    <tr>
                        <td><strong style="font-family: monospace;">${escapeHtml(a.copyCode || a.copyIdentifier || '')}</strong></td>
                        <td>
                            <strong style="color: var(--primary);">${escapeHtml(a.bookTitle || '')}</strong>
                            <div class="text-secondary" style="font-size: 0.8rem;">ISBN: ${escapeHtml(a.bookIsbn || '')}</div>
                        </td>
                        <td>${escapeHtml(a.studentName || '')}</td>
                        <td><code>${escapeHtml(a.registerNumber || '')}</code></td>
                        <td>${escapeHtml(a.allocatedAt ? String(a.allocatedAt).substring(0, 16) : '--')}</td>
                        <td>
                            <strong style="color: #B45309;">${escapeHtml(a.expiresAt ? String(a.expiresAt).substring(0, 16) : '--')}</strong>
                        </td>
                        <td>
                            <span class="badge" style="${isAlloc ? 'background-color: #FEF3C7; color: #B45309; border: 1px solid #FDE68A;' : 'background-color: #E8F8F0; color: #159B50;'}">
                                ${escapeHtml(a.status)}
                            </span>
                        </td>
                        <td style="text-align: right; white-space: nowrap;">
                            ${isAlloc ? `
                                <button class="btn btn-primary btn-sm" style="padding: 4px 10px; font-size: 0.82rem; margin-right: 6px;"
                                        onclick="issueAllocatedCopyToStudent(${a.copyId}, '${escapeJs(a.registerNumber)}')">
                                    Issue Now
                                </button>
                                <button class="btn btn-secondary btn-sm" style="color: var(--danger); border-color: var(--danger); padding: 4px 10px; font-size: 0.82rem;"
                                        onclick="staffReleaseAllocation(${a.reservationId}, '${escapeJs(a.copyCode || '')}', '${escapeJs(a.studentName || '')}')">
                                    Release
                                </button>
                            ` : `
                                <span class="text-secondary" style="font-size: 0.85rem;">Completed</span>
                            `}
                        </td>
                    </tr>
                `;
            }
            tbody.innerHTML = html;
        }
    } catch (e) {
        console.error('Error loading hold shelf allocations', e);
    }
}

window.issueAllocatedCopyToStudent = function (copyId, registerNumber) {
    window.switchDeskTab('tabIssue');
    const regInput = document.getElementById('issueStudentRegNo');
    if (regInput) {
        regInput.value = registerNumber;
        regInput.dispatchEvent(new Event('change'));
    }
};

window.staffReleaseAllocation = async function (reservationId, copyCode, studentName) {
    if (!confirm(`Are you sure you want to release the hold allocation of copy ${copyCode} for ${studentName}?\n\nThe copy will be reallocated to the next student in the queue, or returned to available shelves.`)) {
        return;
    }

    try {
        const resp = await fetch('reservations/cancel', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ reservationId: reservationId })
        });
        const res = await resp.json();
        if (resp.ok && res.success) {
            let msg = 'Allocation released successfully.';
            if (res.data && res.data.copyOutcome === 'REALLOCATED') {
                msg += ` Reallocated to next student: ${res.data.nextStudentName || 'Queue member'} (${res.data.nextStudentRegisterNumber || ''}).`;
            } else {
                msg += ' Copy returned to shelf as AVAILABLE.';
            }
            alert(msg);
            loadHoldShelfAllocations();
            loadAllQueueReservations();
        } else {
            alert('Failed to release allocation: ' + (res.message || 'Error'));
        }
    } catch (e) {
        alert('Network error releasing allocation.');
    }
};

// -----------------------------------------------------------------------------
// Feature 6: Staff Circulation Fines & Administrative Waivers (Tab 8)
// -----------------------------------------------------------------------------
async function loadStaffFines() {
    const tbody = document.getElementById('staffFinesTableBody');
    const searchInput = document.getElementById('staffFineSearchInput');
    const statusSelect = document.getElementById('staffFineStatusFilter');
    if (!tbody) return;

    let url = 'fines?page=1&limit=50';
    if (searchInput && searchInput.value.trim()) {
        url += '&registerNumber=' + encodeURIComponent(searchInput.value.trim());
    }
    if (statusSelect && statusSelect.value && statusSelect.value !== 'ALL') {
        url += '&status=' + encodeURIComponent(statusSelect.value);
    }

    try {
        const resp = await fetch(url);
        if (resp.status === 401 || resp.status === 403) return;
        const json = await resp.json();

        if (json.success && Array.isArray(json.fines)) {
            const list = json.fines;
            if (list.length === 0) {
                tbody.innerHTML = `<tr><td colspan="13" style="text-align: center; padding: 24px;" class="text-secondary">No fine records matching current filter.</td></tr>`;
                return;
            }

            tbody.innerHTML = list.map(f => {
                const outstanding = parseFloat(f.outstandingAmount) || 0;
                const original = parseFloat(f.originalAmount) || 0;
                const paid = parseFloat(f.paidAmount) || 0;
                const waived = parseFloat(f.waivedAmount) || 0;
                const statusLower = (f.status || 'UNPAID').toLowerCase();

                let actionHtml = '';
                if (outstanding > 0 && f.status !== 'PAID' && f.status !== 'WAIVED') {
                    actionHtml = `<button class="btn btn-secondary btn-sm" onclick="openWaiverModal(${f.fineId}, '${escapeJs(f.studentName || '')}', '${escapeJs(f.registerNumber || '')}', ${outstanding})">Waive Fine</button>`;
                } else {
                    actionHtml = `<span class="text-secondary" style="font-size: 0.85rem;">Settled</span>`;
                }

                return `
                    <tr>
                        <td><strong>#${f.fineId}</strong></td>
                        <td><strong>${escapeHtml(f.studentName || '-')}</strong></td>
                        <td><code>${escapeHtml(f.registerNumber || '-')}</code></td>
                        <td>${escapeHtml(f.bookTitle || '-')}</td>
                        <td><code>${escapeHtml(f.copyCode || '-')}</code></td>
                        <td>${escapeHtml(f.returnDate ? f.returnDate.toString() : '-')}</td>
                        <td>${f.overdueDays}d</td>
                        <td>&inr; ${original.toFixed(2)}</td>
                        <td style="color: var(--success);">&inr; ${paid.toFixed(2)}</td>
                        <td style="color: var(--text-secondary);">&inr; ${waived.toFixed(2)}</td>
                        <td style="font-weight: bold; color: ${outstanding > 0 ? 'var(--danger)' : 'var(--success)'};">&inr; ${outstanding.toFixed(2)}</td>
                        <td><span class="badge badge-${statusLower}">${escapeHtml(f.status)}</span></td>
                        <td style="text-align: right;">${actionHtml}</td>
                    </tr>
                `;
            }).join('');
        }
    } catch (e) {
        console.error('Error loading fines', e);
    }
}

function openWaiverModal(fineId, studentName, regNo, outstanding) {
    document.getElementById('waiverFineId').value = fineId;
    document.getElementById('waiverFineRef').innerText = '#' + fineId;
    document.getElementById('waiverStudentName').innerText = studentName;
    document.getElementById('waiverRegNo').innerText = regNo;
    document.getElementById('waiverOutstanding').innerText = 'INR ' + outstanding.toFixed(2);

    const amtInput = document.getElementById('waiverAmountInput');
    amtInput.value = outstanding.toFixed(2);
    amtInput.max = outstanding;
    document.getElementById('waiverReasonInput').value = '';

    openDeskModal('waiverModal');
}

async function submitWaiver() {
    const fineId = document.getElementById('waiverFineId').value;
    const amountVal = parseFloat(document.getElementById('waiverAmountInput').value);
    const reasonVal = (document.getElementById('waiverReasonInput').value || '').trim();

    if (isNaN(amountVal) || amountVal <= 0) {
        alert('Please enter a valid waiver amount greater than zero.');
        return;
    }
    if (!reasonVal) {
        alert('Waiver reason is mandatory for audit compliance.');
        return;
    }

    try {
        const resp = await fetch('fines', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                fineId: parseInt(fineId),
                amount: amountVal,
                reason: reasonVal
            })
        });

        const json = await resp.json();
        if (resp.ok && json.success) {
            alert(json.message || 'Waiver applied successfully.');
            closeDeskModal('waiverModal');
            loadStaffFines();
        } else {
            alert('Waiver failed: ' + (json.message || 'Error occurred. (Note: Only Administrators can grant waivers)'));
        }
    } catch (e) {
        console.error('Error submitting waiver', e);
        alert('Network error while applying waiver.');
    }
}

// -----------------------------------------------------------------------------
// Feature 6: Pending UPI Payment Verifications (Tab 9)
// -----------------------------------------------------------------------------
async function loadPendingVerifications() {
    const tbody = document.getElementById('pendingVerifTableBody');
    const badge = document.getElementById('pendingVerifTabCountBadge');
    if (!tbody) return;

    try {
        const resp = await fetch('payments/verify');
        if (resp.status === 401 || resp.status === 403) return;
        const json = await resp.json();

        if (json.success && Array.isArray(json.pendingVerifications)) {
            const list = json.pendingVerifications;
            if (badge) badge.textContent = list.length;

            if (list.length === 0) {
                tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; padding: 24px;" class="text-secondary">No pending UPI payments awaiting verification.</td></tr>`;
                return;
            }

            tbody.innerHTML = list.map(p => `
                <tr>
                    <td><strong>${escapeHtml(p.paymentCode || '-')}</strong></td>
                    <td><strong>${escapeHtml(p.studentName || '-')}</strong></td>
                    <td><code>${escapeHtml(p.registerNumber || '-')}</code></td>
                    <td>${escapeHtml(p.bookTitle || '-')}</td>
                    <td style="font-weight: bold; color: var(--primary);">&inr; ${parseFloat(p.amount || 0).toFixed(2)}</td>
                    <td><code style="font-size: 1.05rem; letter-spacing: 1px; font-weight: bold; color: #159B50;">${escapeHtml(p.referenceId || '-')}</code></td>
                    <td>${escapeHtml(p.createdAt ? p.createdAt.toString().substring(0, 16) : '-')}</td>
                    <td style="text-align: right; white-space: nowrap;">
                        <button class="btn btn-primary btn-sm" style="padding: 4px 10px; margin-right: 6px;" onclick="verifyPayment(${p.paymentId})">
                            &#10003; Verify Payment
                        </button>
                        <button class="btn btn-secondary btn-sm" style="color: var(--danger); border-color: var(--danger); padding: 4px 10px;" onclick="openRejectPaymentModal(${p.paymentId})">
                            Reject
                        </button>
                    </td>
                </tr>
            `).join('');
        }
    } catch (e) {
        console.error('Error loading pending verifications', e);
    }
}

async function verifyPayment(paymentId) {
    if (!confirm(`Are you sure you want to verify payment #${paymentId}?\n\nThis confirms that the 12-digit UTR has been matched against the college bank account statement. An official receipt will be generated.`)) {
        return;
    }

    try {
        const resp = await fetch('payments/verify', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                action: 'verify',
                paymentId: paymentId
            })
        });

        const json = await resp.json();
        if (resp.ok && json.success) {
            alert(json.message || 'Payment verified successfully.');
            loadPendingVerifications();
            loadStaffFines();
        } else {
            alert('Verification failed: ' + (json.message || 'Error'));
        }
    } catch (e) {
        console.error('Error verifying payment', e);
        alert('Network error while verifying payment.');
    }
}

function openRejectPaymentModal(paymentId) {
    document.getElementById('rejectPaymentId').value = paymentId;
    document.getElementById('rejectReasonInput').value = '';
    openDeskModal('rejectPaymentModal');
}

async function submitPaymentRejection() {
    const paymentId = document.getElementById('rejectPaymentId').value;
    const reason = (document.getElementById('rejectReasonInput').value || '').trim();

    if (!reason) {
        alert('Rejection reason is mandatory.');
        return;
    }

    try {
        const resp = await fetch('payments/verify', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                action: 'reject',
                paymentId: parseInt(paymentId),
                reason: reason
            })
        });

        const json = await resp.json();
        if (resp.ok && json.success) {
            alert(json.message || 'Payment rejected.');
            closeDeskModal('rejectPaymentModal');
            loadPendingVerifications();
            loadStaffFines();
        } else {
            alert('Rejection failed: ' + (json.message || 'Error'));
        }
    } catch (e) {
        console.error('Error rejecting payment', e);
        alert('Network error while rejecting payment.');
    }
}

function openDeskModal(modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.add('show');
}

function closeDeskModal(modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.remove('show');
}


