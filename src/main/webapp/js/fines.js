/**
 * PSG iTech Library - Feature 6: Fine Calculation & UPI Payment Client Logic
 */

let activeFineRecord = null;
let currentPaymentAttempt = null;

function loadMyFines() {
    fetch('my-fines')
        .then(response => {
            if (response.status === 401) {
                window.location.href = 'login.html?redirect=my-fines.html';
                return null;
            }
            return response.json();
        })
        .then(data => {
            if (!data || !data.success) {
                console.error('Failed to load fines:', data ? data.message : 'Unknown error');
                return;
            }

            renderSummary(data.summary);
            renderProjectedFines(data.projectedFines);
            renderFinesTable(data.fines);
            renderPaymentsTable(data.payments);
        })
        .catch(err => {
            console.error('Error fetching my fines:', err);
        });
}

function renderSummary(summary) {
    if (!summary) return;

    const outstanding = parseFloat(summary.totalOutstanding) || 0;
    const paid = parseFloat(summary.totalPaid) || 0;
    const waived = parseFloat(summary.totalWaived) || 0;
    const count = summary.unpaidCount || 0;

    document.getElementById('sumOutstanding').innerText = 'INR ' + outstanding.toFixed(2);
    document.getElementById('sumPaid').innerText = 'INR ' + paid.toFixed(2);
    document.getElementById('sumWaived').innerText = 'INR ' + waived.toFixed(2);
    document.getElementById('sumUnpaidCount').innerText = `${count} open fine${count === 1 ? '' : 's'}`;

    const badge = document.getElementById('borrowingEligibilityBadge');
    const banner = document.getElementById('blockedAlertBanner');
    const cardOut = document.getElementById('cardOutstanding');

    if (summary.borrowingBlocked) {
        badge.innerText = 'Borrowing Blocked (Dues Pending)';
        badge.style.backgroundColor = '#FDEDEC';
        badge.style.color = '#C0392B';
        badge.style.borderColor = '#E6B0AA';
        banner.style.display = 'block';
        cardOut.className = 'summary-card alert';
    } else {
        badge.innerText = 'Account Good Standing (Eligible)';
        badge.style.backgroundColor = '#EAFAF1';
        badge.style.color = '#1E8449';
        badge.style.borderColor = '#A9DFBF';
        banner.style.display = 'none';
        cardOut.className = 'summary-card clean';
    }
}

function renderProjectedFines(projectedList) {
    const section = document.getElementById('projectedFinesSection');
    const tbody = document.getElementById('projectedFinesTableBody');

    if (!projectedList || projectedList.length === 0) {
        section.style.display = 'none';
        tbody.innerHTML = '';
        return;
    }

    section.style.display = 'block';
    tbody.innerHTML = projectedList.map(item => `
        <tr>
            <td style="font-weight: 500;">${escapeHtml(item.bookTitle)}</td>
            <td><code>${escapeHtml(item.copyCode)}</code></td>
            <td>${escapeHtml(item.dueDate)}</td>
            <td style="color: var(--danger); font-weight: bold;">${item.overdueDays} day${item.overdueDays === 1 ? '' : 's'}</td>
            <td>&inr; ${parseFloat(item.ratePerDay).toFixed(2)}/day</td>
            <td style="color: var(--danger); font-weight: bold;">&inr; ${parseFloat(item.projectedAmount).toFixed(2)}</td>
        </tr>
    `).join('');
}

function renderFinesTable(fines) {
    const tbody = document.getElementById('finesTableBody');

    if (!fines || fines.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="11" style="text-align: center; padding: 32px;" class="text-secondary">
                    No fine records found. You have no overdue fines on your account!
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = fines.map(f => {
        const outstanding = parseFloat(f.outstandingAmount) || 0;
        const original = parseFloat(f.originalAmount) || 0;
        const paid = parseFloat(f.paidAmount) || 0;
        const waived = parseFloat(f.waivedAmount) || 0;
        const statusLower = (f.status || 'UNPAID').toLowerCase();
        const isComp = (f.fineType === 'COMPENSATION');

        let actionHtml = '';
        if (outstanding > 0 && f.status !== 'PAID' && f.status !== 'WAIVED') {
            actionHtml = `<button class="btn btn-primary btn-sm" onclick="openPaymentModal(${f.fineId}, '${escapeJs(f.bookTitle)}', ${outstanding})">Pay via UPI</button>`;
        } else {
            actionHtml = `<span class="text-secondary" style="font-size: 0.85rem;">Settled</span>`;
        }

        return `
            <tr>
                <td><strong>#${f.fineId}</strong></td>
                <td style="font-weight: 500;">
                    ${isComp ? '<span class="badge" style="background:#FDEDEC; color:#C0392B; border:1px solid #E6B0AA; font-size:0.75rem; margin-right:4px;">COMPENSATION</span>' : ''}
                    ${escapeHtml(f.bookTitle || 'Resource')}
                </td>
                <td><code>${escapeHtml(f.copyCode || '-')}</code></td>
                <td>${escapeHtml(f.returnDate ? f.returnDate.toString() : '-')}</td>
                <td>${isComp ? '<span class="text-secondary">Assessed Case</span>' : (f.overdueDays + ' days')}</td>
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

function renderPaymentsTable(payments) {
    const tbody = document.getElementById('paymentsTableBody');

    if (!payments || payments.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7" style="text-align: center; padding: 24px;" class="text-secondary">
                    No payment attempts recorded yet.
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = payments.map(p => {
        const amount = parseFloat(p.amount) || 0;
        const statusLower = (p.status || 'INITIATED').toLowerCase();
        let actions = '';

        if (p.status === 'SUCCESS' || p.status === 'REFUNDED') {
            actions = `<a href="receipt.html?paymentId=${p.paymentId}" target="_blank" class="btn btn-secondary btn-sm">&#128438; Receipt</a>`;
        } else if (p.status === 'INITIATED') {
            actions = `
                <button class="btn btn-primary btn-sm" onclick="resumeInitiatedPayment(${p.paymentId}, '${escapeJs(p.paymentCode)}', ${amount})">Submit UTR</button>
                <button class="btn btn-secondary btn-sm" onclick="cancelPayment(${p.paymentId})">Cancel</button>
            `;
        } else if (p.status === 'REJECTED' && !p.disputed) {
            actions = `<button class="btn btn-secondary btn-sm" onclick="openDisputeModal(${p.paymentId})">Dispute</button>`;
        } else if (p.disputed) {
            actions = `<span class="badge" style="background:#FDEDEC; color:#C0392B; border:1px solid #E6B0AA;">Dispute Under Review</span>`;
        } else {
            actions = `<span class="text-secondary" style="font-size: 0.85rem;">Awaiting Staff</span>`;
        }

        return `
            <tr>
                <td><strong>${escapeHtml(p.paymentCode || '-')}</strong></td>
                <td>${escapeHtml(p.createdAt ? p.createdAt.toString().substring(0, 16) : '-')}</td>
                <td style="font-weight: bold;">&inr; ${amount.toFixed(2)}</td>
                <td>${escapeHtml(p.method || 'UPI_MANUAL')}</td>
                <td><code>${escapeHtml(p.referenceId || 'Pending Entry')}</code></td>
                <td><span class="badge badge-${statusLower}">${escapeHtml(p.status)}</span></td>
                <td style="text-align: right;">${actions}</td>
            </tr>
        `;
    }).join('');
}

function openPaymentModal(fineId, bookTitle, outstanding) {
    activeFineRecord = { fineId, bookTitle, outstanding };
    currentPaymentAttempt = null;

    document.getElementById('modalFineRef').innerText = '#' + fineId;
    document.getElementById('modalFineBook').innerText = bookTitle;
    document.getElementById('modalFineOutstanding').innerText = 'INR ' + outstanding.toFixed(2);

    const payInput = document.getElementById('payAmountInput');
    payInput.value = outstanding.toFixed(2);
    payInput.max = outstanding;
    payInput.min = "1.00";

    document.getElementById('modalStep1').style.display = 'block';
    document.getElementById('modalStep2').style.display = 'none';

    openModal('upiPaymentModal');
}

function generateIdempotencyKey() {
    if (window.crypto && window.crypto.randomUUID) {
        return window.crypto.randomUUID();
    }
    return 'idem-' + Date.now() + '-' + Math.floor(Math.random() * 1000000);
}

function initiatePayment() {
    if (!activeFineRecord) return;

    const amountInput = document.getElementById('payAmountInput');
    const amountVal = parseFloat(amountInput.value);

    if (isNaN(amountVal) || amountVal < 1.00) {
        alert('Please enter a valid amount of at least INR 1.00.');
        return;
    }
    if (amountVal > activeFineRecord.outstanding) {
        alert('Payment amount cannot exceed outstanding fine balance of INR ' + activeFineRecord.outstanding.toFixed(2));
        return;
    }

    const btn = document.getElementById('btnInitiatePayment');
    btn.disabled = true;
    btn.innerText = 'Initiating Payment...';

    const idempotencyKey = generateIdempotencyKey();

    fetch('payments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            action: 'initiate',
            fineId: activeFineRecord.fineId,
            amount: amountVal,
            idempotencyKey: idempotencyKey
        })
    })
    .then(res => res.json())
    .then(data => {
        btn.disabled = false;
        btn.innerText = 'Proceed to UPI Payment ->';

        if (!data.success) {
            alert(data.message || 'Failed to initiate payment.');
            return;
        }

        const p = data.payment;
        const inst = data.instructions || {};
        currentPaymentAttempt = p;

        showPaymentInstructions(p, inst);
    })
    .catch(err => {
        btn.disabled = false;
        btn.innerText = 'Proceed to UPI Payment ->';
        console.error('Error initiating payment:', err);
        alert('Communication error while initiating payment.');
    });
}

function showPaymentInstructions(payment, instructions) {
    document.getElementById('modalStep1').style.display = 'none';
    document.getElementById('modalStep2').style.display = 'block';

    const amount = parseFloat(payment.amount) || 0;
    document.getElementById('modalPayAmountText').innerText = 'INR ' + amount.toFixed(2);
    document.getElementById('modalPaymentCodeText').innerText = 'Code: ' + (payment.paymentCode || '-');

    const upiUri = instructions.upiUri || `upi://pay?pa=library@psgitech.ac.in&pn=PSG+iTech+Library&am=${amount.toFixed(2)}&cu=INR&tn=${payment.paymentCode}`;
    const upiId = instructions.upiId || 'library@psgitech.ac.in';

    document.getElementById('modalUpiIdText').innerText = upiId;

    // Set QR code image using safe dynamic URL
    const qrImg = document.getElementById('upiQrImg');
    qrImg.src = `https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(upiUri)}`;

    // Set deep link
    const deepBtn = document.getElementById('upiDeepLinkBtn');
    deepBtn.href = upiUri;

    document.getElementById('upiRefInput').value = '';
}

function resumeInitiatedPayment(paymentId, paymentCode, amount) {
    currentPaymentAttempt = { paymentId, paymentCode, amount };
    const upiUri = `upi://pay?pa=library@psgitech.ac.in&pn=PSG+iTech+Library&am=${amount.toFixed(2)}&cu=INR&tn=${paymentCode}`;

    showPaymentInstructions(currentPaymentAttempt, { upiUri, upiId: 'library@psgitech.ac.in' });
    openModal('upiPaymentModal');
}

function submitUpiReference() {
    if (!currentPaymentAttempt || !currentPaymentAttempt.paymentId) {
        alert('Active payment session missing.');
        return;
    }

    const refInput = document.getElementById('upiRefInput');
    const refVal = (refInput.value || '').trim();

    if (!refVal || refVal.length !== 12 || !/^[A-Za-z0-9]{12}$/.test(refVal)) {
        alert('Please enter a valid 12-digit UPI reference number (UTR) found on your UPI receipt.');
        refInput.focus();
        return;
    }

    const btn = document.getElementById('btnSubmitRef');
    btn.disabled = true;
    btn.innerText = 'Submitting...';

    fetch('payments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            action: 'submit_reference',
            paymentId: currentPaymentAttempt.paymentId,
            reference: refVal
        })
    })
    .then(res => res.json())
    .then(data => {
        btn.disabled = false;
        btn.innerText = 'Submit for Verification';

        if (!data.success) {
            alert(data.message || 'Failed to submit UPI reference.');
            return;
        }

        alert('Reference submitted successfully! Staff will verify the payment against the bank account statement.');
        closeModal('upiPaymentModal');
        loadMyFines();
    })
    .catch(err => {
        btn.disabled = false;
        btn.innerText = 'Submit for Verification';
        console.error('Error submitting reference:', err);
        alert('Communication error while submitting reference.');
    });
}

function cancelActivePayment() {
    if (!currentPaymentAttempt || !currentPaymentAttempt.paymentId) {
        closeModal('upiPaymentModal');
        return;
    }
    cancelPayment(currentPaymentAttempt.paymentId);
    closeModal('upiPaymentModal');
}

function cancelPayment(paymentId) {
    if (!confirm('Are you sure you want to cancel this payment attempt?')) return;

    fetch('payments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            action: 'cancel',
            paymentId: paymentId
        })
    })
    .then(res => res.json())
    .then(data => {
        if (!data.success) {
            alert(data.message || 'Failed to cancel payment.');
            return;
        }
        loadMyFines();
    })
    .catch(err => {
        console.error('Error cancelling payment:', err);
        alert('Communication error while cancelling payment.');
    });
}

function openDisputeModal(paymentId) {
    document.getElementById('disputePaymentId').value = paymentId;
    document.getElementById('disputeReasonInput').value = '';
    openModal('disputeModal');
}

function submitDispute() {
    const paymentId = document.getElementById('disputePaymentId').value;
    const reason = (document.getElementById('disputeReasonInput').value || '').trim();

    if (!reason) {
        alert('Please enter an explanation for the dispute.');
        return;
    }

    fetch('payments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            action: 'dispute',
            paymentId: parseInt(paymentId),
            reason: reason
        })
    })
    .then(res => res.json())
    .then(data => {
        if (!data.success) {
            alert(data.message || 'Failed to submit dispute.');
            return;
        }
        alert('Dispute submitted successfully to the Administrator.');
        closeModal('disputeModal');
        loadMyFines();
    })
    .catch(err => {
        console.error('Error submitting dispute:', err);
        alert('Communication error while submitting dispute.');
    });
}

function openModal(modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.add('show');
}

function closeModal(modalId) {
    const el = document.getElementById(modalId);
    if (el) el.classList.remove('show');
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

function escapeJs(str) {
    if (!str) return '';
    return String(str).replace(/'/g, "\\'").replace(/"/g, '\\"');
}

