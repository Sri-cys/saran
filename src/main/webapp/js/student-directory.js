/**
 * PSG iTech Library - Peer-to-Peer Book Borrowing Hub Controller
 * Handles open peer loan searching, request to borrow initiation,
 * incoming requests with Module 13 OTP handover flow, and sent request verification.
 */

(function () {
    'use strict';

    let state = {
        currentUserId: null,
        query: '',
        selectedLoan: null,
        peerLoans: [],
        incoming: [],
        outgoing: []
    };

    let searchTimeout = null;

    document.addEventListener('DOMContentLoaded', () => {
        resolveUserSession();
        initEventHandlers();
        loadAllData();
    });

    function resolveUserSession() {
        if (typeof window.getCurrentUser === 'function') {
            const user = window.getCurrentUser();
            if (user) {
                state.currentUserId = user.userId;
            }
        }
        if (!state.currentUserId) {
            try {
                const stored = sessionStorage.getItem('psgitech_user') || localStorage.getItem('psgitech_user');
                if (stored) {
                    const u = JSON.parse(stored);
                    state.currentUserId = u.userId;
                }
            } catch (ignored) {}
        }
    }

    function initEventHandlers() {
        const form = document.getElementById('peerSearchForm');
        const input = document.getElementById('peerSearchInput');
        const btnClear = document.getElementById('btnResetSearch');

        if (form) {
            form.addEventListener('submit', (e) => {
                e.preventDefault();
                state.query = (input ? input.value : '').trim();
                if (btnClear) btnClear.style.display = state.query ? 'inline-block' : 'none';
                loadPeerLoans();
            });
        }

        if (input) {
            input.addEventListener('input', (e) => {
                clearTimeout(searchTimeout);
                searchTimeout = setTimeout(() => {
                    state.query = e.target.value.trim();
                    if (btnClear) btnClear.style.display = state.query ? 'inline-block' : 'none';
                    loadPeerLoans();
                }, 400);
            });
        }

        if (btnClear) {
            btnClear.addEventListener('click', () => {
                if (input) input.value = '';
                state.query = '';
                btnClear.style.display = 'none';
                loadPeerLoans();
            });
        }
    }

    async function loadAllData() {
        await Promise.all([
            loadPeerLoans(),
            loadIncomingRequests(),
            loadOutgoingRequests()
        ]);
    }

    // =========================================================================
    // 1. Available Peer Loans Search & Display
    // =========================================================================
    window.loadPeerLoans = async function () {
        const container = document.getElementById('peerLoansContainer');
        const summary = document.getElementById('peerLoansSummary');

        try {
            const qParam = encodeURIComponent(state.query || '');
            const resp = await fetch(`interchanges?type=peer-loans&q=${qParam}`);
            if (resp.status === 401) {
                if (container) {
                    container.innerHTML = `
                        <div style="text-align: center; padding: 32px 20px; background: #FFFFFF; border-radius: 8px; border: 1px solid var(--border);">
                            <p class="text-secondary" style="margin-bottom: 12px;">Please sign in with your student credentials to view and borrow peer-held books.</p>
                            <a href="login.html?redirect=student-directory.html" class="btn btn-primary btn-sm">Log In as Student</a>
                        </div>
                    `;
                }
                if (summary) summary.textContent = 'Sign in required to view active student book holders.';
                return;
            }

            const res = await resp.json();
            if (!res.success) {
                if (container) container.innerHTML = `<div class="alert alert-danger">${escapeHtml(res.message || 'Error loading peer loans.')}</div>`;
                return;
            }

            const loans = res.data || [];
            state.peerLoans = loans;

            if (summary) {
                summary.textContent = `Found ${loans.length} book(s) held by fellow students${state.query ? ` matching "${escapeHtml(state.query)}"` : ''}.`;
            }

            if (!container) return;

            if (loans.length === 0) {
                container.innerHTML = `
                    <div style="text-align: center; padding: 40px 20px; background: #FFFFFF; border-radius: 8px; border: 1px dashed var(--border); color: var(--text-secondary);">
                        <p style="font-size: 1.05rem; margin-bottom: 6px;">No peer-held books matching your search.</p>
                        <p style="font-size: 0.88rem; margin: 0;">Try searching for another textbook title, author, or check the central catalog.</p>
                    </div>
                `;
                return;
            }

            container.innerHTML = loans.map(loan => {
                const titleEsc = escapeHtml(loan.bookTitle || 'Untitled Book');
                const authorEsc = escapeHtml(loan.bookAuthor || 'Unknown Author');
                const copyCodeEsc = escapeHtml(loan.copyCode || `PSG-BK${loan.bookId}-C01`);
                const holderNameEsc = escapeHtml(loan.holderName || 'Fellow Student');
                const holderRollEsc = escapeHtml(loan.holderRollNo || '—');
                const deptEsc = escapeHtml(loan.department || 'Engineering');
                const dueDateEsc = escapeHtml(loan.dueDate || '—');

                let actionHtml = '';
                if (loan.requestedByMe) {
                    actionHtml = `<span class="badge" style="background: #FEF3C7; color: #B45309; border: 1px solid #FDE68A; padding: 8px 14px; font-weight: bold; border-radius: 6px;">Requested by You</span>`;
                } else if (loan.isRequested) {
                    actionHtml = `<span class="badge" style="background: #F3F4F6; color: #6B7280; border: 1px solid #E5E7EB; padding: 8px 14px; font-weight: bold; border-radius: 6px;">Transfer Pending</span>`;
                } else {
                    actionHtml = `
                        <button type="button" class="btn btn-primary" 
                                style="background-color: #1F3A60; border-color: #1F3A60; padding: 8px 18px; font-size: 0.92rem; font-weight: 600;"
                                onclick="openRequestBorrowModal(${loan.transactionId}, '${escapeJs(titleEsc)}', '${escapeJs(holderNameEsc)}', '${escapeJs(holderRollEsc)}', '${escapeJs(dueDateEsc)}')">
                            Request to Borrow
                        </button>
                    `;
                }

                return `
                    <div class="peer-holder-card">
                        <div style="flex: 1; min-width: 260px;">
                            <div style="display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap;">
                                <h3 style="font-size: 1.15rem; color: var(--primary); margin: 0; line-height: 1.3;">${titleEsc}</h3>
                                <span class="peer-meta-tag" style="font-family: monospace; font-weight: bold; color: #203B60;">${copyCodeEsc}</span>
                            </div>
                            <div class="text-secondary" style="font-size: 0.92rem; margin-top: 4px;">
                                by <strong>${authorEsc}</strong>
                            </div>
                            <div style="margin-top: 8px; font-size: 0.88rem; display: flex; flex-wrap: wrap; gap: 12px; align-items: center;">
                                <span>Holding Student: <strong>${holderNameEsc}</strong> (<code style="color: var(--primary);">${holderRollEsc}</code>)</span>
                                <span>Dept: <strong>${deptEsc}</strong></span>
                                <span>Due Date: <strong style="color: #B45309;">${dueDateEsc}</strong></span>
                            </div>
                        </div>
                        <div style="display: flex; align-items: center; justify-content: flex-end;">
                            ${actionHtml}
                        </div>
                    </div>
                `;
            }).join('');

        } catch (err) {
            console.error('Failed to load peer loans:', err);
            if (container) container.innerHTML = `<div class="alert alert-danger">Network or server error loading peer loans.</div>`;
        }
    };

    // =========================================================================
    // 2. Request to Borrow Dialog & Submission
    // =========================================================================
    window.openRequestBorrowModal = function (txId, title, holderName, holderRoll, dueDate) {
        state.selectedLoan = { txId, title, holderName, holderRoll, dueDate };
        const modal = document.getElementById('requestBorrowModal');
        const titleEl = document.getElementById('reqBookTitle');
        const nameEl = document.getElementById('reqHolderName');
        const rollEl = document.getElementById('reqHolderRoll');
        const dueEl = document.getElementById('reqDueDate');
        const noteEl = document.getElementById('reqNoteInput');

        if (titleEl) titleEl.textContent = title;
        if (nameEl) nameEl.textContent = holderName;
        if (rollEl) rollEl.textContent = holderRoll;
        if (dueEl) dueEl.textContent = dueDate;
        if (noteEl) noteEl.value = '';

        if (modal) modal.style.display = 'flex';
    };

    window.closeRequestBorrowModal = function () {
        const modal = document.getElementById('requestBorrowModal');
        if (modal) modal.style.display = 'none';
        state.selectedLoan = null;
    };

    window.submitBorrowRequest = async function () {
        if (!state.selectedLoan) return;
        const noteEl = document.getElementById('reqNoteInput');
        const btn = document.getElementById('btnSubmitBorrowRequest');
        const note = noteEl ? noteEl.value.trim() : '';

        if (btn) {
            btn.disabled = true;
            btn.textContent = 'Sending Request...';
        }

        try {
            const resp = await fetch('interchanges', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: JSON.stringify({
                    transactionId: state.selectedLoan.txId,
                    requestNote: note
                })
            });

            const res = await resp.json();
            if (resp.ok && res.success) {
                showToast('Request sent successfully! The holding student has been notified.');
                closeRequestBorrowModal();
                loadPeerLoans();
                loadOutgoingRequests();
            } else {
                alert(res.message || 'Failed to submit borrow request.');
            }
        } catch (err) {
            console.error('Error submitting borrow request:', err);
            alert('A network error occurred while submitting your request.');
        } finally {
            if (btn) {
                btn.disabled = false;
                btn.textContent = 'Send Request to Borrow';
            }
        }
    };

    // =========================================================================
    // 3. Incoming Requests (Action Required)
    // =========================================================================
    window.loadIncomingRequests = async function () {
        const container = document.getElementById('incomingRequestsContainer');
        const badge = document.getElementById('incomingBadge');

        try {
            const resp = await fetch('interchanges?type=incoming');
            if (resp.status === 401) return;

            const res = await resp.json();
            if (!res.success) return;

            const requests = res.data || [];
            state.incoming = requests;

            if (badge) badge.textContent = requests.length;

            if (!container) return;

            if (requests.length === 0) {
                container.innerHTML = `
                    <div style="text-align: center; padding: 28px; background: #FAF9F6; border: 1px dashed var(--border); border-radius: 8px; color: var(--text-secondary); font-size: 0.92rem;">
                        No pending incoming requests from other students.
                    </div>
                `;
                return;
            }

            container.innerHTML = requests.map(req => {
                const titleEsc = escapeHtml(req.bookTitle || 'Book Title');
                const copyCodeEsc = escapeHtml(req.copyCode || '');
                const requesterNameEsc = escapeHtml(req.toStudentName || 'Fellow Student');
                const requesterRollEsc = escapeHtml(req.toStudentRegNo || '—');
                const dueDateEsc = escapeHtml(req.originalDueDate || '—');
                const noteEsc = req.requestNote ? `<div style="font-size: 0.85rem; color: #5F6368; font-style: italic; margin-top: 4px;">&ldquo;${escapeHtml(req.requestNote)}&rdquo;</div>` : '';

                return `
                    <div class="request-box" style="border-left: 4px solid var(--primary);">
                        <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 8px;">
                            <div>
                                <h4 style="margin: 0; color: var(--primary); font-size: 1.05rem;">${titleEsc}</h4>
                                <div style="font-size: 0.82rem; font-family: monospace; color: #5F6368; margin-top: 2px;">${copyCodeEsc}</div>
                            </div>
                            <span class="badge" style="background: #E8F5EE; color: #159B50; font-size: 0.75rem; font-weight: bold;">ACTION REQUIRED</span>
                        </div>

                        <div style="margin-top: 8px; font-size: 0.88rem;">
                            Requested by: <strong>${requesterNameEsc}</strong> (<code style="color: var(--primary);">${requesterRollEsc}</code>)
                        </div>
                        <div style="font-size: 0.82rem; color: #B45309; margin-top: 2px;">
                            Current Loan Due Date: <strong>${dueDateEsc}</strong>
                        </div>
                        ${noteEsc}

                        <div style="display: flex; gap: 8px; margin-top: 14px; justify-content: flex-end;">
                            <button type="button" class="btn btn-outline btn-sm" onclick="declineInterchangeRequest(${req.interchangeId}, '${escapeJs(titleEsc)}')">
                                Decline
                            </button>
                            <button type="button" class="btn btn-primary btn-sm" style="background-color: #1F3A60; border-color: #1F3A60;" 
                                    onclick="acceptInterchangeRequest(${req.interchangeId}, '${escapeJs(requesterNameEsc)}')">
                                &#10003; Accept Request
                            </button>
                        </div>
                    </div>
                `;
            }).join('');

        } catch (err) {
            console.error('Failed to load incoming requests:', err);
        }
    };

    // Accept Request -> Initiates Module 13 OTP Handover & displays OTP to holder
    window.acceptInterchangeRequest = async function (interchangeId, requesterName) {
        try {
            const resp = await fetch('interchanges/otp/generate', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: JSON.stringify({ interchangeId })
            });

            const res = await resp.json();
            if (resp.ok && res.success) {
                const otp = res.otp || 'CHECK EMAIL';
                displayHandoverOtpModal(otp, requesterName);
                loadIncomingRequests();
                loadOutgoingRequests();
            } else {
                alert(res.message || 'Failed to initiate OTP handover.');
            }
        } catch (err) {
            console.error('Error accepting interchange request:', err);
            alert('A network error occurred while generating the handover verification code.');
        }
    };

    function displayHandoverOtpModal(otpCode, requesterName) {
        const modal = document.getElementById('handoverOtpModal');
        const codeEl = document.getElementById('displayOtpCode');
        if (codeEl) codeEl.textContent = otpCode;
        if (modal) modal.style.display = 'flex';
    }

    window.closeHandoverOtpModal = function () {
        const modal = document.getElementById('handoverOtpModal');
        if (modal) modal.style.display = 'none';
    };

    // Decline Request
    window.declineInterchangeRequest = async function (interchangeId, bookTitle) {
        const reason = prompt(`Decline borrow request for "${bookTitle}"?\nOptional reason:`, 'Currently studying for upcoming exams');
        if (reason === null) return; // user cancelled prompt

        try {
            const resp = await fetch('interchanges/reject', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: JSON.stringify({
                    interchangeId,
                    reason: reason.trim() || 'Declined by holding student.'
                })
            });

            const res = await resp.json();
            if (resp.ok && res.success) {
                showToast('Borrow request declined.');
                loadIncomingRequests();
                loadPeerLoans();
            } else {
                alert(res.message || 'Failed to decline request.');
            }
        } catch (err) {
            console.error('Error declining request:', err);
            alert('A network error occurred while declining the request.');
        }
    };

    // =========================================================================
    // 4. My Sent Requests
    // =========================================================================
    window.loadOutgoingRequests = async function () {
        const container = document.getElementById('sentRequestsContainer');
        const badge = document.getElementById('outgoingBadge');

        try {
            const resp = await fetch('interchanges?type=outgoing');
            if (resp.status === 401) return;

            const res = await resp.json();
            if (!res.success) return;

            const requests = res.data || [];
            state.outgoing = requests;

            if (badge) badge.textContent = requests.length;

            if (!container) return;

            if (requests.length === 0) {
                container.innerHTML = `
                    <div style="text-align: center; padding: 28px; background: #FAF9F6; border: 1px dashed var(--border); border-radius: 8px; color: var(--text-secondary); font-size: 0.92rem;">
                        You have not sent any borrow requests yet.
                    </div>
                `;
                return;
            }

            container.innerHTML = requests.map(req => {
                const titleEsc = escapeHtml(req.bookTitle || 'Book Title');
                const copyCodeEsc = escapeHtml(req.copyCode || '');
                const holderNameEsc = escapeHtml(req.fromStudentName || 'Fellow Student');
                const holderRollEsc = escapeHtml(req.fromStudentRegNo || '—');
                const statusStr = (req.status || 'REQUESTED').toUpperCase();

                let statusBadge = '';
                let actionArea = '';

                if (statusStr === 'COMPLETED') {
                    statusBadge = `<span class="badge" style="background: #E8F5EE; color: #159B50; font-weight: bold;">COMPLETED</span>`;
                    actionArea = `<div style="font-size: 0.85rem; color: #159B50; font-weight: 500; margin-top: 8px;">&#10003; Book checked out to your account.</div>`;
                } else if (statusStr === 'REJECTED') {
                    statusBadge = `<span class="badge" style="background: #FEE2E2; color: #B91C1C; font-weight: bold;">DECLINED</span>`;
                    actionArea = `<div style="font-size: 0.82rem; color: #B91C1C; margin-top: 6px;">${escapeHtml(req.rejectionReason || 'Declined by holder.')}</div>`;
                } else if (statusStr === 'CANCELLED') {
                    statusBadge = `<span class="badge" style="background: #F3F4F6; color: #6B7280; font-weight: bold;">CANCELLED</span>`;
                } else {
                    // PENDING / REQUESTED
                    statusBadge = `<span class="badge" style="background: #FEF3C7; color: #B45309; font-weight: bold;">PENDING HANDOVER</span>`;
                    actionArea = `
                        <div style="margin-top: 12px; background: var(--background-warm); padding: 10px 12px; border-radius: 6px; border: 1px solid var(--border);">
                            <div style="font-size: 0.82rem; font-weight: bold; color: var(--primary); margin-bottom: 6px;">
                                Confirm Physical Handover (Enter 6-Digit OTP from Holder):
                            </div>
                            <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap;">
                                <input type="text" id="otpInput_${req.interchangeId}" class="form-control" 
                                       placeholder="6-Digit OTP" maxlength="6" 
                                       style="width: 140px; text-transform: uppercase; letter-spacing: 2px; font-weight: bold; text-align: center; padding: 6px 8px; font-size: 0.95rem;">
                                <button type="button" class="btn btn-primary btn-sm" style="background-color: #159B50; border-color: #159B50;" 
                                        onclick="confirmHandoverOtp(${req.interchangeId})">
                                    Confirm Handover
                                </button>
                                <button type="button" class="btn btn-outline btn-sm" onclick="cancelInterchangeRequest(${req.interchangeId})">
                                    Cancel
                                </button>
                            </div>
                        </div>
                    `;
                }

                return `
                    <div class="request-box">
                        <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 8px;">
                            <div>
                                <h4 style="margin: 0; color: var(--primary); font-size: 1.05rem;">${titleEsc}</h4>
                                <div style="font-size: 0.82rem; font-family: monospace; color: #5F6368; margin-top: 2px;">${copyCodeEsc}</div>
                            </div>
                            ${statusBadge}
                        </div>

                        <div style="margin-top: 8px; font-size: 0.88rem;">
                            Holding Student: <strong>${holderNameEsc}</strong> (<code style="color: var(--primary);">${holderRollEsc}</code>)
                        </div>
                        <div style="font-size: 0.82rem; color: #5F6368; margin-top: 2px;">
                            Requested on: ${req.requestedAt ? String(req.requestedAt).substring(0, 16) : 'Recently'}
                        </div>

                        ${actionArea}
                    </div>
                `;
            }).join('');

        } catch (err) {
            console.error('Failed to load outgoing requests:', err);
        }
    };

    // Confirm Physical Handover OTP by Recipient
    window.confirmHandoverOtp = async function (interchangeId) {
        const input = document.getElementById(`otpInput_${interchangeId}`);
        const otp = input ? input.value.trim() : '';

        if (!otp || otp.length < 6) {
            alert('Please enter the 6-digit handover verification code provided by the holding student.');
            return;
        }

        try {
            const resp = await fetch('interchanges/accept', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: JSON.stringify({
                    interchangeId,
                    otp
                })
            });

            const res = await resp.json();
            if (resp.ok && res.success) {
                showToast('Handover confirmed! The textbook has been successfully transferred to your library account.');
                loadOutgoingRequests();
                loadPeerLoans();
            } else {
                alert(res.message || 'Handover verification failed. Invalid or expired OTP code.');
            }
        } catch (err) {
            console.error('Error confirming handover OTP:', err);
            alert('A network error occurred while confirming the handover.');
        }
    };

    // Cancel Sent Request
    window.cancelInterchangeRequest = async function (interchangeId) {
        if (!confirm('Cancel this borrow request?')) return;

        try {
            const resp = await fetch('interchanges/cancel', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest'
                },
                body: JSON.stringify({
                    interchangeId,
                    reason: 'Cancelled by requesting student.'
                })
            });

            const res = await resp.json();
            if (resp.ok && res.success) {
                showToast('Borrow request cancelled.');
                loadOutgoingRequests();
                loadPeerLoans();
            } else {
                alert(res.message || 'Failed to cancel request.');
            }
        } catch (err) {
            console.error('Error cancelling request:', err);
            alert('A network error occurred while cancelling the request.');
        }
    };

    // =========================================================================
    // Helpers & Utilities
    // =========================================================================
    function showToast(msg) {
        const toast = document.getElementById('toastPopup');
        const text = document.getElementById('toastMessage');
        if (!toast || !text) {
            alert(msg);
            return;
        }
        text.textContent = msg;
        toast.style.display = 'block';
        setTimeout(() => {
            toast.style.display = 'none';
        }, 4000);
    }

    function escapeHtml(str) {
        if (str === null || str === undefined) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function escapeJs(str) {
        if (!str) return '';
        return String(str).replace(/'/g, "\\'").replace(/"/g, '&quot;');
    }

})();
