document.addEventListener('DOMContentLoaded', () => {
    initCasesPage();
});

async function initCasesPage() {
    setupProfileDropdown();
    await loadMyCases();
}

function setupProfileDropdown() {
    const trigger = document.getElementById('profileTrigger');
    const menu = document.getElementById('profileDropdownMenu');
    if (trigger && menu) {
        trigger.addEventListener('click', (e) => {
            e.stopPropagation();
            menu.classList.toggle('show');
        });
        document.addEventListener('click', () => {
            menu.classList.remove('show');
        });
    }
}

async function loadMyCases() {
    const container = document.getElementById('casesList');
    try {
        const resp = await fetch('cases/my');
        if (resp.status === 401) {
            window.location.href = 'login.html?redirect=my-cases.html';
            return;
        }
        const data = await resp.json();
        if (!data.success) {
            container.innerHTML = `<div class="alert alert-danger">${data.message || 'Failed to load cases.'}</div>`;
            return;
        }

        const cases = data.cases || [];
        updateSummary(cases);

        if (cases.length === 0) {
            container.innerHTML = `
                <div style="background: #fff; border: 1px solid var(--border); border-radius: 6px; padding: 48px 24px; text-align: center;">
                    <h3 style="color: var(--primary); margin-top: 0;">No Active or Past Cases</h3>
                    <p style="color: var(--text-secondary); max-width: 480px; margin: 8px auto 20px;">
                        You have not reported any books lost, and have no damage assessments on your account. Your library record is in good standing.
                    </p>
                    <a href="my-borrowings.html" class="btn btn-primary">View My Borrowed Books</a>
                </div>
            `;
            return;
        }

        container.innerHTML = cases.map(c => renderCaseCard(c)).join('');

    } catch (e) {
        console.error('Error loading cases', e);
        container.innerHTML = `<div class="alert alert-danger">Error loading cases: ${e.message}</div>`;
    }
}

function updateSummary(cases) {
    const total = cases.length;
    const unresolved = cases.filter(c => ['ASSESSED', 'PAYMENT_PENDING', 'REPLACEMENT_PENDING', 'REPLACEMENT_SUBMITTED'].includes(c.status)).length;
    const replacements = cases.filter(c => ['REPLACEMENT_PENDING', 'REPLACEMENT_SUBMITTED'].includes(c.status)).length;
    const resolved = cases.filter(c => ['RESOLVED_PAID', 'RESOLVED_REPLACED', 'WAIVED'].includes(c.status)).length;

    document.getElementById('valTotalCases').textContent = total;
    document.getElementById('valUnresolved').textContent = unresolved;
    document.getElementById('valReplacements').textContent = replacements;
    document.getElementById('valResolved').textContent = resolved;
}

function renderCaseCard(c) {
    const statusClass = 'badge-' + (c.status || '').toLowerCase();
    const isAssessed = c.status === 'ASSESSED';
    const isPaymentPending = c.status === 'PAYMENT_PENDING';
    const isRepPending = c.status === 'REPLACEMENT_PENDING';
    const isRepSubmitted = c.status === 'REPLACEMENT_SUBMITTED';
    const isResolvedPaid = c.status === 'RESOLVED_PAID';
    const isResolvedReplaced = c.status === 'RESOLVED_REPLACED';

    let actionBlock = '';
    if (isAssessed) {
        actionBlock = `
            <div style="background: #FFFDF0; border: 1px solid #F9E79F; border-radius: 4px; padding: 16px; margin-top: 14px;">
                <h4 style="margin: 0 0 8px 0; color: var(--primary);">Assessment Complete — Select Resolution Option</h4>
                <p style="font-size: 0.9rem; color: var(--text-secondary); margin-bottom: 12px;">
                    PSG iTech Library policy gives you the choice to pay assessed institutional compensation or provide an identical physical replacement copy within 30 days.
                </p>
                <div style="display: flex; gap: 12px; flex-wrap: wrap;">
                    <button class="btn btn-primary" onclick="chooseResolution(${c.caseId}, 'PAYMENT')">
                        Pay Compensation (₹${Number(c.finalAmount || 0).toFixed(2)})
                    </button>
                    <button class="btn btn-secondary" onclick="chooseResolution(${c.caseId}, 'REPLACEMENT')">
                        Offer Replacement Copy (ISBN: ${c.bookIsbn || 'Catalog'})
                    </button>
                </div>
            </div>
        `;
    } else if (isPaymentPending) {
        actionBlock = `
            <div style="background: #FDEDEC; border: 1px solid #E6B0AA; border-radius: 4px; padding: 14px; margin-top: 14px; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px;">
                <div>
                    <strong>Payment Pending:</strong> Compensation fine of ₹${Number(c.finalAmount || 0).toFixed(2)} has been assigned.
                    <div style="font-size: 0.85rem; color: var(--danger);">Unpaid obligation blocks new borrowings.</div>
                </div>
                <a href="my-fines.html" class="btn btn-primary">Pay via UPI in Fines &amp; Dues</a>
            </div>
        `;
    } else if (isRepPending) {
        actionBlock = `
            <div style="background: #FEF9E7; border: 1px solid #F5CBA7; border-radius: 4px; padding: 14px; margin-top: 14px;">
                <h4 style="margin: 0 0 6px 0; color: #D35400;">Replacement Copy Awaited</h4>
                <div style="font-size: 0.9rem; line-height: 1.5;">
                    Please procure an identical copy matching <strong>ISBN ${c.bookIsbn || '-'}</strong> in <strong>New or Like-New</strong> condition.
                    <br>Hand in the copy at the circulation counter on or before: <strong>${c.replacementDeadline || 'within 30 days'}</strong>.
                </div>
                <div style="margin-top: 10px;">
                    <button class="btn btn-secondary" style="font-size: 0.85rem; padding: 6px 12px;" onclick="chooseResolution(${c.caseId}, 'PAYMENT')">
                        Switch to Monetary Payment instead (₹${Number(c.finalAmount || 0).toFixed(2)})
                    </button>
                </div>
            </div>
        `;
    } else if (isRepSubmitted) {
        actionBlock = `
            <div style="background: #E8F8F5; border: 1px solid #A3E4D7; border-radius: 4px; padding: 14px; margin-top: 14px;">
                <strong style="color: #117864;">Replacement Copy Handed In:</strong>
                Awaiting staff barcode verification and ISBN validation. Once accepted, your obligation will clear automatically.
            </div>
        `;
    } else if (isResolvedReplaced) {
        actionBlock = `
            <div style="background: #EAFAF1; border: 1px solid #A9DFBF; border-radius: 4px; padding: 12px 14px; margin-top: 14px; color: #1E8449;">
                <strong>Case Resolved:</strong> Physical replacement copy accepted and accessioned into library catalog. Obligation cleared.
            </div>
        `;
    } else if (isResolvedPaid) {
        actionBlock = `
            <div style="background: #EAFAF1; border: 1px solid #A9DFBF; border-radius: 4px; padding: 12px 14px; margin-top: 14px; color: #1E8449; display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap;">
                <div><strong>Case Resolved:</strong> Compensation paid in full. Obligation cleared.</div>
                <a href="my-fines.html" class="btn btn-secondary" style="font-size: 0.85rem;">View Receipt in Fines</a>
            </div>
        `;
    }

    let breakdownHtml = '';
    if (c.priceUsed != null) {
        breakdownHtml = `
            <table class="breakdown-table">
                <thead>
                    <tr>
                        <th>Catalog / Base Price</th>
                        <th>Depreciation</th>
                        <th>Processing Fee</th>
                        <th>Damage Factor</th>
                        <th>Total Assessed</th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td>₹${Number(c.priceUsed || 0).toFixed(2)}</td>
                        <td>-₹${Number(c.depreciationApplied || 0).toFixed(2)} ${c.ageFlag ? '<span style="font-size:0.75rem; color:#888;">(Book age >1yr)</span>' : ''}</td>
                        <td>+₹${Number(c.processingFee || 0).toFixed(2)}</td>
                        <td>${c.severityFraction != null ? (Number(c.severityFraction) * 100).toFixed(0) + '%' : '100%'}</td>
                        <td style="font-weight: bold; color: var(--primary);">₹${Number(c.finalAmount || 0).toFixed(2)}</td>
                    </tr>
                </tbody>
            </table>
        `;
    }

    return `
        <div class="case-card" id="case-${c.caseId}">
            <div class="case-header">
                <div>
                    <h3 style="margin: 0 0 4px 0; color: var(--primary);">${escapeHtml(c.bookTitle || 'Book Title')}</h3>
                    <div style="font-size: 0.88rem; color: var(--text-secondary);">
                        Copy: <strong>${escapeHtml(c.copyCode || '-')}</strong> &bull; ISBN: ${escapeHtml(c.bookIsbn || '-')} &bull; Case #${c.caseId}
                    </div>
                </div>
                <div style="text-align: right;">
                    <span class="badge ${statusClass}">${(c.status || '').replace('_', ' ')}</span>
                    <div style="font-size: 0.8rem; color: var(--text-secondary); margin-top: 4px;">
                        Reported: ${formatDate(c.reportedAt)}
                    </div>
                </div>
            </div>

            <div style="font-size: 0.9rem; margin-bottom: 8px;">
                Type: <strong>${c.caseType || '-'}</strong>
                ${c.severity ? ` &bull; Severity: <strong>${c.severity}</strong>` : ''}
                ${c.copyDisposition ? ` &bull; Disposition: <strong>${c.copyDisposition}</strong>` : ''}
                ${c.reportNote ? `<div style="margin-top: 4px; font-style: italic; color: #555;">Note: "${escapeHtml(c.reportNote)}"</div>` : ''}
            </div>

            ${breakdownHtml}
            ${actionBlock}

            <div style="margin-top: 14px; text-align: right;">
                <button class="btn btn-secondary" style="font-size: 0.8rem; padding: 4px 8px;" onclick="toggleAuditHistory(${c.caseId})">
                    View Audit Timeline
                </button>
            </div>
            <div id="timeline-${c.caseId}" style="display: none; margin-top: 10px;"></div>
        </div>
    `;
}

async function chooseResolution(caseId, choice) {
    const confirmMsg = choice === 'PAYMENT'
        ? 'Confirm choosing monetary compensation payment? A fine will be generated on your account.'
        : 'Confirm choosing physical replacement copy? You will have 30 days to hand in an identical copy.';

    if (!confirm(confirmMsg)) return;

    try {
        const resp = await fetch('cases/choose', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ caseId, choice })
        });
        const data = await resp.json();
        if (!data.success) {
            alert('Failed: ' + (data.message || 'Error selecting option'));
            return;
        }

        if (choice === 'PAYMENT') {
            window.location.href = 'my-fines.html';
        } else {
            await loadMyCases();
        }
    } catch (e) {
        alert('Network error: ' + e.message);
    }
}

async function toggleAuditHistory(caseId) {
    const el = document.getElementById(`timeline-${caseId}`);
    if (!el) return;
    if (el.style.display === 'block') {
        el.style.display = 'none';
        return;
    }

    el.innerHTML = '<div style="font-size:0.85rem; color:#888;">Loading timeline...</div>';
    el.style.display = 'block';

    try {
        const resp = await fetch(`cases/detail?caseId=${caseId}`);
        const data = await resp.json();
        if (!data.success || !data.events) {
            el.innerHTML = '<div style="font-size:0.85rem; color:#888;">No history events found.</div>';
            return;
        }

        const events = data.events;
        el.innerHTML = `
            <div class="timeline">
                ${events.map(ev => `
                    <div class="timeline-item">
                        <div style="font-size:0.88rem; font-weight:600;">${ev.eventType} &bull; ${ev.actorRole} #${ev.actorUserId}</div>
                        <div style="font-size:0.85rem; color:#444;">${escapeHtml(ev.details || '')}</div>
                        <div class="timeline-time">${formatDate(ev.createdAt)}</div>
                    </div>
                `).join('')}
            </div>
        `;
    } catch (e) {
        el.innerHTML = `<div style="color:red; font-size:0.85rem;">Error loading timeline: ${e.message}</div>`;
    }
}

function formatDate(ts) {
    if (!ts) return '-';
    try {
        const d = new Date(ts);
        return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
    } catch (e) {
        return ts;
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
