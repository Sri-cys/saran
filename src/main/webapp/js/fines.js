/**
 * PSG iTech Library - Feature 6 & Feature 14: Fine Management & Razorpay Payment Integration
 */

let activeFineRecord = null;
let currentPaymentAttempt = null;
let currentStudentProfile = { name: '', registerNumber: '', department: '' };
let currentOutstandingFines = [];

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

            // Populate Student Profile Bar
            if (data.summary) {
                currentStudentProfile.name = data.summary.studentName || 'Student Member';
                currentStudentProfile.registerNumber = data.summary.studentRegisterNumber || data.summary.registerNumber || '--';
                currentStudentProfile.department = data.summary.studentDepartment || 'Student Member';

                const nameEl = document.getElementById('hdrStudentName');
                const regEl = document.getElementById('hdrStudentRegNo');
                const deptEl = document.getElementById('hdrStudentDept');
                const avatarEl = document.getElementById('hdrStudentAvatar');

                if (nameEl) nameEl.innerText = currentStudentProfile.name;
                if (regEl) regEl.innerText = currentStudentProfile.registerNumber;
                if (deptEl) deptEl.innerText = currentStudentProfile.department;
                if (avatarEl && currentStudentProfile.name) {
                    avatarEl.innerText = currentStudentProfile.name.trim().charAt(0).toUpperCase();
                }
            }

            renderSummary(data.summary);
            renderBulkPaySection(data.summary, data.fines);
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

    const sumOutEl = document.getElementById('sumOutstanding');
    const sumPaidEl = document.getElementById('sumPaid');
    const sumWaivedEl = document.getElementById('sumWaived');
    const sumCountEl = document.getElementById('sumUnpaidCount');

    if (sumOutEl) sumOutEl.innerHTML = '&inr; ' + outstanding.toFixed(2);
    if (sumPaidEl) sumPaidEl.innerHTML = '&inr; ' + paid.toFixed(2);
    if (sumWaivedEl) sumWaivedEl.innerHTML = '&inr; ' + waived.toFixed(2);
    if (sumCountEl) sumCountEl.innerText = `${count} open fine${count === 1 ? '' : 's'}`;

    const badge = document.getElementById('borrowingEligibilityBadge');
    const banner = document.getElementById('blockedAlertBanner');
    const cardOut = document.getElementById('cardOutstanding');

    if (summary.borrowingBlocked) {
        if (badge) {
            badge.innerText = 'Borrowing Blocked (Dues Pending)';
            badge.style.backgroundColor = '#FDEDEC';
            badge.style.color = '#C0392B';
            badge.style.borderColor = '#E6B0AA';
        }
        if (banner) banner.style.display = 'block';
        if (cardOut) cardOut.className = 'summary-card alert';
    } else {
        if (badge) {
            badge.innerText = 'Account Good Standing (Eligible)';
            badge.style.backgroundColor = '#EAFAF1';
            badge.style.color = '#1E8449';
            badge.style.borderColor = '#A9DFBF';
        }
        if (banner) banner.style.display = 'none';
        if (cardOut) cardOut.className = 'summary-card clean';
    }
}

function renderBulkPaySection(summary, fines) {
    const section = document.getElementById('bulkPaySection');
    const notice = document.getElementById('noOutstandingFinesNotice');
    if (!section) return;

    // Filter active unpaid or partially paid fines
    currentOutstandingFines = (fines || []).filter(f => {
        const out = parseFloat(f.outstandingAmount) || 0;
        return out > 0 && f.status !== 'PAID' && f.status !== 'WAIVED';
    });

    const count = currentOutstandingFines.length;
    let totalOutstanding = 0;
    currentOutstandingFines.forEach(f => {
        totalOutstanding += parseFloat(f.outstandingAmount) || 0;
    });

    if (count >= 2) {
        section.style.display = 'flex';
        const titleEl = document.getElementById('bulkPayTitle');
        const subEl = document.getElementById('bulkPaySubtitle');
        const btnText = document.getElementById('btnPayAllText');

        if (titleEl) titleEl.innerText = `Pay All ${count} Outstanding Fines`;
        if (subEl) subEl.innerHTML = `Combine all <strong>${count}</strong> open library fines totaling <strong>&inr; ${totalOutstanding.toFixed(2)}</strong> into a single transaction.`;
        if (btnText) btnText.innerHTML = `PAY ALL &ndash; &inr; ${totalOutstanding.toFixed(2)}`;

        const btn = document.getElementById('btnPayAllFines');
        if (btn) {
            btn.disabled = false;
        }

        if (notice) notice.style.display = 'none';

    } else if (count === 1) {
        // Only 1 fine - individual Pay Now button is already clearly presented
        section.style.display = 'none';
        if (notice) notice.style.display = 'none';

    } else {
        // 0 outstanding fines
        section.style.display = 'none';
        if (notice) notice.style.display = 'inline-block';
    }
}

function renderProjectedFines(projectedList) {
    const section = document.getElementById('projectedFinesSection');
    const tbody = document.getElementById('projectedFinesTableBody');
    if (!section || !tbody) return;

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
    if (!tbody) return;

    if (!fines || fines.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="10" style="text-align: center; padding: 36px;" class="text-secondary">
                    &#127881; No fine records found. You have no overdue fines on your account!
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = fines.map(f => {
        const outstanding = parseFloat(f.outstandingAmount) || 0;
        const original = parseFloat(f.originalAmount) || 0;
        const paid = parseFloat(f.paidAmount) || 0;
        const statusLower = (f.status || 'UNPAID').toLowerCase();
        const isComp = (f.fineType === 'COMPENSATION');

        // Fine Reason
        let reasonText = '';
        if (isComp) {
            reasonText = '<span class="badge" style="background:#FDEDEC; color:#C0392B; border:1px solid #E6B0AA; font-size:0.75rem; margin-right:4px;">COMPENSATION</span> Lost / Damaged Book';
        } else {
            reasonText = f.overdueDays > 0 ? `Overdue (${f.overdueDays} days late)` : 'Overdue';
        }

        // Payment Action Buttons
        let actionHtml = '';
        if (outstanding > 0 && f.status !== 'PAID' && f.status !== 'WAIVED') {
            actionHtml = `
                <div style="display: flex; flex-direction: column; align-items: flex-end; gap: 4px;">
                    <button class="btn-rzp-pay" id="btnPay_${f.fineId}" onclick="payNowRazorpay(${f.fineId}, '${escapeJs(f.bookTitle)}', ${outstanding})">
                        &#128179; Pay Now &ndash; &inr; ${outstanding.toFixed(2)}
                    </button>
                    <button type="button" class="btn-upi-manual" onclick="openPaymentModal(${f.fineId}, '${escapeJs(f.bookTitle)}', ${outstanding})">
                        &#128241; UPI QR / UTR
                    </button>
                </div>
            `;
        } else {
            actionHtml = `<span class="badge badge-paid" style="font-size: 0.85rem;">&#10003; Settled</span>`;
        }

        return `
            <tr>
                <td><strong>#${f.fineId}</strong></td>
                <td>
                    <div style="font-weight: 600; color: var(--primary);">${escapeHtml(f.bookTitle || 'Library Resource')}</div>
                    <small class="text-secondary">${reasonText}</small>
                </td>
                <td><code>${escapeHtml(f.copyCode || '-')}</code></td>
                <td>${escapeHtml(f.returnDate ? f.returnDate.toString() : (f.createdAt ? f.createdAt.toString().substring(0, 10) : '-'))}</td>
                <td>${escapeHtml(f.dueDate ? f.dueDate.toString() : '-')}</td>
                <td>&inr; ${original.toFixed(2)}</td>
                <td style="color: var(--success); font-weight: 500;">&inr; ${paid.toFixed(2)}</td>
                <td style="font-weight: bold; color: ${outstanding > 0 ? 'var(--danger)' : 'var(--success)'}; font-size: 1.05rem;">
                    &inr; ${outstanding.toFixed(2)}
                </td>
                <td><span class="badge badge-${statusLower}">${escapeHtml(f.status)}</span></td>
                <td style="text-align: right;">${actionHtml}</td>
            </tr>
        `;
    }).join('');
}

function renderPaymentsTable(payments) {
    const tbody = document.getElementById('paymentsTableBody');
    if (!tbody) return;

    if (!payments || payments.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="8" style="text-align: center; padding: 24px;" class="text-secondary">
                    No payment attempts recorded yet.
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = payments.map(p => {
        const amount = parseFloat(p.amount) || 0;
        const statusLower = (p.status || 'INITIATED').toLowerCase();
        const paymentType = p.paymentType || (p.finesSettledCount > 1 ? 'BULK' : 'INDIVIDUAL');
        const typeBadge = (paymentType === 'BULK')
            ? `<span class="badge" style="background: #FEF9E7; color: #B7950B; border: 1px solid #F9E79F; font-size: 0.78rem;">BULK (${p.finesSettledCount || (p.settledFineIds ? p.settledFineIds.length : 'Multiple')})</span>`
            : `<span class="badge" style="background: #EBF5FB; color: #2874A6; border: 1px solid #AED6F1; font-size: 0.78rem;">INDIVIDUAL</span>`;

        let settledText = '-';
        if (p.settledFineIds && p.settledFineIds.length > 0) {
            settledText = p.settledFineIds.map(id => '#' + id).join(', ');
        } else if (p.fineId > 0) {
            settledText = '#' + p.fineId;
        }

        let actions = '';
        if (p.status === 'SUCCESS' || p.status === 'REFUNDED') {
            actions = `<a href="receipt.html?paymentId=${p.paymentId}" target="_blank" class="btn btn-secondary btn-sm" style="display: inline-flex; align-items: center; gap: 4px;">&#128438; Receipt</a>`;
        } else if (p.status === 'INITIATED' && p.method !== 'RAZORPAY') {
            actions = `
                <button class="btn btn-primary btn-sm" onclick="resumeInitiatedPayment(${p.paymentId}, '${escapeJs(p.paymentCode)}', ${amount})">Submit UTR</button>
                <button class="btn btn-secondary btn-sm" onclick="cancelPayment(${p.paymentId})">Cancel</button>
            `;
        } else if (p.status === 'INITIATED' && p.method === 'RAZORPAY') {
            actions = `<span class="text-secondary" style="font-size: 0.85rem;">Checkout Open</span>`;
        } else if (p.status === 'REJECTED' && !p.disputed) {
            actions = `<button class="btn btn-secondary btn-sm" onclick="openDisputeModal(${p.paymentId})">Dispute</button>`;
        } else if (p.disputed) {
            actions = `<span class="badge" style="background:#FDEDEC; color:#C0392B; border:1px solid #E6B0AA;">Dispute Under Review</span>`;
        } else {
            actions = `<span class="text-secondary" style="font-size: 0.85rem;">Awaiting Staff</span>`;
        }

        const dateStr = p.createdAt ? p.createdAt.toString().substring(0, 16) : '-';
        const displayCode = p.receiptNumber || p.referenceId || p.paymentCode || '-';

        return `
            <tr>
                <td><strong>${escapeHtml(displayCode)}</strong></td>
                <td>${escapeHtml(dateStr)}</td>
                <td style="font-weight: bold; color: var(--primary);">&inr; ${amount.toFixed(2)}</td>
                <td>${typeBadge}</td>
                <td><small>${escapeHtml(settledText)}</small></td>
                <td><code>${escapeHtml(p.method || 'RAZORPAY')}</code></td>
                <td><span class="badge badge-${statusLower}">${escapeHtml(p.status)}</span></td>
                <td style="text-align: right;">${actions}</td>
            </tr>
        `;
    }).join('');
}

// ============================================================================
// RAZORPAY PAYMENT FLOW (INDIVIDUAL & PAY ALL)
// ============================================================================

/**
 * Initiates Razorpay checkout for an individual fine.
 */
function payNowRazorpay(fineId, bookTitle, outstandingAmount) {
    const btn = document.getElementById(`btnPay_${fineId}`);
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = 'Creating Order...';
    }

    fetch('payments/razorpay/create-order', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            action: 'create_order',
            type: 'INDIVIDUAL',
            fineId: fineId
        })
    })
    .then(res => res.json())
    .then(data => {
        if (!data || !data.success) {
            alert(data ? data.message : 'Failed to create payment order. Please try again.');
            if (btn) {
                btn.disabled = false;
                btn.innerHTML = `&#128179; Pay Now &ndash; &inr; ${outstandingAmount.toFixed(2)}`;
            }
            return;
        }

        launchRazorpayCheckout(data, `Fine Settlement #${fineId} - ${bookTitle}`, () => {
            if (btn) {
                btn.disabled = false;
                btn.innerHTML = `&#128179; Pay Now &ndash; &inr; ${outstandingAmount.toFixed(2)}`;
            }
        });
    })
    .catch(err => {
        console.error('Error creating Razorpay order:', err);
        alert('Communication error while contacting payment gateway.');
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = `&#128179; Pay Now &ndash; &inr; ${outstandingAmount.toFixed(2)}`;
        }
    });
}

/**
 * Initiates Razorpay checkout for ALL outstanding fines combined.
 */
function payAllRazorpay() {
    if (!currentOutstandingFines || currentOutstandingFines.length === 0) {
        alert('You have no outstanding fines to settle.');
        return;
    }

    const fineIds = currentOutstandingFines.map(f => f.fineId);
    const btn = document.getElementById('btnPayAllFines');
    const btnText = document.getElementById('btnPayAllText');

    if (btn) btn.disabled = true;
    if (btnText) btnText.innerText = 'Creating Bulk Order...';

    fetch('payments/razorpay/create-order', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            action: 'create_order',
            type: 'BULK',
            fineIds: fineIds
        })
    })
    .then(res => res.json())
    .then(data => {
        if (!data || !data.success) {
            alert(data ? data.message : 'Failed to create bulk payment order. Please try again.');
            if (btn) btn.disabled = false;
            loadMyFines();
            return;
        }

        launchRazorpayCheckout(data, `Bulk Fine Settlement (${data.finesCount} Fines)`, () => {
            if (btn) btn.disabled = false;
            loadMyFines();
        });
    })
    .catch(err => {
        console.error('Error creating bulk Razorpay order:', err);
        alert('Communication error while contacting payment gateway.');
        if (btn) btn.disabled = false;
        loadMyFines();
    });
}

/**
 * Opens the Razorpay Standard Checkout Modal using official checkout.js SDK.
 */
function launchRazorpayCheckout(orderData, description, onFinally) {
    if (typeof Razorpay === 'undefined') {
        alert('Razorpay Checkout SDK is still loading or offline. Please check your internet connection.');
        if (onFinally) onFinally();
        return;
    }

    const options = {
        key: orderData.keyId,
        amount: orderData.amountPaise,
        currency: orderData.currency || 'INR',
        name: 'PSG iTech Library',
        description: description,
        image: 'images/psgitech-logo.png',
        order_id: orderData.orderId,
        handler: function (response) {
            // response: { razorpay_payment_id, razorpay_order_id, razorpay_signature }
            verifyRazorpayPayment(response.razorpay_order_id, response.razorpay_payment_id, response.razorpay_signature, onFinally);
        },
        modal: {
            ondismiss: function () {
                cancelRazorpayPayment(orderData.orderId, 'User closed payment window');
                if (onFinally) onFinally();
            }
        },
        prefill: {
            name: currentStudentProfile.name || '',
            email: '',
            contact: ''
        },
        theme: {
            color: '#002147'
        }
    };

    try {
        const rzp = new Razorpay(options);
        rzp.on('payment.failed', function (resp) {
            handleRazorpayFailure(orderData.orderId, resp.error, onFinally);
        });
        rzp.open();
    } catch (e) {
        console.error('Failed to open Razorpay modal:', e);
        alert('Failed to launch payment checkout: ' + e.message);
        if (onFinally) onFinally();
    }
}

/**
 * Sends HMAC signature to server for cryptographic verification and atomic fine settlement.
 */
function verifyRazorpayPayment(orderId, paymentId, signature, onFinally) {
    // Show temporary overlay or notification
    fetch('payments/razorpay/verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            action: 'verify',
            razorpay_order_id: orderId,
            razorpay_payment_id: paymentId,
            razorpay_signature: signature
        })
    })
    .then(res => res.json())
    .then(data => {
        if (!data || !data.success) {
            alert(data ? data.message : 'Payment verification failed. Please contact the library staff.');
            if (onFinally) onFinally();
            loadMyFines();
            return;
        }

        // Show Success Confirmation Modal
        showPaymentConfirmation(data);
        if (onFinally) onFinally();
    })
    .catch(err => {
        console.error('Error verifying payment:', err);
        alert('Communication error while verifying payment with library server.');
        if (onFinally) onFinally();
        loadMyFines();
    });
}

function showPaymentConfirmation(data) {
    const receiptNoEl = document.getElementById('confReceiptNumber');
    const payIdEl = document.getElementById('confPaymentId');
    const amtEl = document.getElementById('confAmountPaid');
    const typeEl = document.getElementById('confPaymentType');
    const finesEl = document.getElementById('confSettledFines');
    const linkEl = document.getElementById('confReceiptLink');

    if (receiptNoEl) receiptNoEl.innerText = data.receiptNumber || 'RCPT-ISSUED';
    if (payIdEl) payIdEl.innerText = data.referenceId || data.paymentId || 'pay_verified';
    if (amtEl) amtEl.innerHTML = '&inr; ' + (parseFloat(data.amount) || 0).toFixed(2);
    if (typeEl) typeEl.innerText = (data.paymentType === 'BULK') ? 'Bulk Fine Settlement' : 'Individual Fine Settlement';

    const fIds = data.settledFineIds || [];
    if (finesEl) {
        finesEl.innerText = fIds.length > 0 ? fIds.map(id => '#' + id).join(', ') : 'All Associated Fines';
    }

    if (linkEl && data.paymentId) {
        linkEl.href = `receipt.html?paymentId=${data.paymentId}`;
    }

    openModal('razorpaySuccessModal');
    loadMyFines();
}

function closeConfirmationModal() {
    closeModal('razorpaySuccessModal');
    loadMyFines();
}

function cancelRazorpayPayment(orderId, reason) {
    fetch('payments/razorpay/cancel', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            action: 'cancel',
            orderId: orderId,
            reason: reason
        })
    })
    .then(() => {
        loadMyFines();
    })
    .catch(err => {
        console.warn('Error recording payment cancellation:', err);
    });
}

function handleRazorpayFailure(orderId, error, onFinally) {
    const errorMsg = error ? (error.description || error.reason || 'Payment declined by bank') : 'Payment failed';
    fetch('payments/razorpay/failed', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            action: 'failed',
            orderId: orderId,
            reason: errorMsg
        })
    })
    .finally(() => {
        alert(`Payment Failed: ${errorMsg}\nYour fine balance is unchanged. You may retry payment anytime.`);
        if (onFinally) onFinally();
        loadMyFines();
    });
}

// ============================================================================
// LEGACY / MANUAL UPI PAYMENT MODAL (FALLBACK & RECORD SUBMISSION)
// ============================================================================

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
        btn.innerText = 'Proceed to UPI QR ->';

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
        btn.innerText = 'Proceed to UPI QR ->';
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

    const qrImg = document.getElementById('upiQrImg');
    qrImg.src = `https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(upiUri)}`;

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
