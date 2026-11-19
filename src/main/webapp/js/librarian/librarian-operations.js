/**
 * PSG iTech Central Library - Librarian Operations Module
 * Manages Acquisitions, Bulk CSV Import, QR/Barcode Scanner, Due Calendar, Queries, and Purchases.
 */

import { CTX, apiFetch, showToast } from './librarian-api.js';

let activeCalendarDate = null;
let currentMonth = new Date().getMonth() + 1;
let currentYear = new Date().getFullYear();
let videoStream = null;

export function initOperationsModule() {
    setupAcquisitions();
    setupCsvImport();
    setupBarcodeScanner();
    setupDueCalendar();
    setupStudentQueries();
    setupPurchases();
}

// ============================================================================
// 1. ACQUISITIONS
// ============================================================================
export async function loadAcquisitions() {
    const statusFilter = document.getElementById('acqStatusFilter')?.value || 'ALL';
    const deptFilter = document.getElementById('acqDeptFilter')?.value || 'ALL';
    const tbody = document.getElementById('acqTableBody');
    if (!tbody) return;

    tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; padding: 24px;">Loading acquisition requests...</td></tr>';

    try {
        const res = await apiFetch(`/api/librarian/operations/acquisitions?status=${encodeURIComponent(statusFilter)}&department=${encodeURIComponent(deptFilter)}`);
        if (!res.success || !res.data || res.data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; padding: 24px; color: var(--navy-light);">No acquisition requests found matching criteria.</td></tr>';
            return;
        }

        tbody.innerHTML = res.data.map(req => `
            <tr>
                <td><strong>#${req.requestId}</strong></td>
                <td>
                    <div style="font-weight: 600; color: var(--navy-primary);">${escapeHtml(req.title)}</div>
                    <div style="font-size: 11px; color: var(--navy-light);">by ${escapeHtml(req.author)} ${req.edition ? '• ' + escapeHtml(req.edition) : ''}</div>
                </td>
                <td><span style="font-family: monospace; font-size: 11px;">${escapeHtml(req.isbn || 'N/A')}</span></td>
                <td><span class="badge" style="background: rgba(10,37,64,0.08);">${escapeHtml(req.department || 'General')}</span></td>
                <td>
                    <span class="badge" style="background: ${req.demandCount > 2 ? '#E02020; color: white;' : 'rgba(0,212,178,0.15); color: #008770;'} font-weight: bold;">
                        ${req.demandCount} student(s)
                    </span>
                </td>
                <td>${formatAcqStatus(req.status)}</td>
                <td style="font-size: 12px;">${req.requesterName ? escapeHtml(req.requesterName) : 'Student'}</td>
                <td>
                    <div style="display: flex; gap: 4px; flex-wrap: wrap;">
                        ${req.status === 'REQUESTED' ? `
                            <button class="btn-quick btn-quick-primary" style="padding: 3px 8px; font-size: 11px;" onclick="window.librarianOperations.openStatusModal(${req.requestId}, 'APPROVED')">Approve</button>
                            <button class="btn-quick" style="padding: 3px 8px; font-size: 11px; color: #E02020;" onclick="window.librarianOperations.openStatusModal(${req.requestId}, 'REJECTED')">Reject</button>
                        ` : ''}
                        ${req.status === 'APPROVED' ? `
                            <button class="btn-quick btn-quick-primary" style="padding: 3px 8px; font-size: 11px;" onclick="window.librarianOperations.openStatusModal(${req.requestId}, 'ORDERED')">Mark Ordered</button>
                        ` : ''}
                        ${req.status === 'ORDERED' ? `
                            <button class="btn-quick btn-quick-primary" style="padding: 3px 8px; font-size: 11px;" onclick="window.librarianOperations.openConvertModal(${req.requestId}, '${escapeHtml(req.title)}', '${escapeHtml(req.author)}', '${escapeHtml(req.isbn || '')}', '${escapeHtml(req.department || '')}', ${req.demandCount})">Convert to Inventory</button>
                        ` : ''}
                        ${req.status === 'RECEIVED' ? `
                            <span style="font-size: 11px; color: #008770; font-weight: bold;">✔ In Catalog</span>
                        ` : ''}
                    </div>
                </td>
            </tr>
        `).join('');
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: #E02020; padding: 24px;">Failed to load acquisitions: ${escapeHtml(err.message)}</td></tr>`;
    }
}

function formatAcqStatus(status) {
    switch (status) {
        case 'REQUESTED': return '<span class="status-pill status-pill-under-review">Requested</span>';
        case 'APPROVED': return '<span class="status-pill status-pill-available">Approved</span>';
        case 'ORDERED': return '<span class="status-pill" style="background: rgba(74,144,226,0.15); color: #4A90E2;">Ordered</span>';
        case 'RECEIVED': return '<span class="status-pill status-pill-available">Received</span>';
        case 'REJECTED': return '<span class="status-pill status-pill-lost">Rejected</span>';
        default: return `<span class="status-pill">${escapeHtml(status)}</span>`;
    }
}

function setupAcquisitions() {
    document.getElementById('acqStatusFilter')?.addEventListener('change', loadAcquisitions);
    document.getElementById('acqDeptFilter')?.addEventListener('change', loadAcquisitions);

    document.getElementById('acqStatusForm')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const requestId = document.getElementById('acqStatusRequestId').value;
        const status = document.getElementById('acqStatusTarget').value;
        const remarks = document.getElementById('acqStatusRemarks').value;

        try {
            const res = await apiFetch('/api/librarian/operations/acquisitions/status', {
                method: 'POST',
                body: JSON.stringify({ requestId: parseInt(requestId), status, remarks })
            });
            if (res.success) {
                showToast(`Acquisition request #${requestId} marked as ${status}.`, 'success');
                document.getElementById('acqStatusModal').classList.remove('show');
                loadAcquisitions();
            } else {
                showToast(res.error?.message || 'Failed to update request', 'error');
            }
        } catch (err) {
            showToast(err.message, 'error');
        }
    });

    document.getElementById('convertAcqForm')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const requestId = document.getElementById('convertAcqId').value;
        const category = document.getElementById('convertCategory').value;
        const copies = parseInt(document.getElementById('convertCopies').value) || 1;
        const floor = document.getElementById('convertFloor').value;
        const block = document.getElementById('convertBlock').value;
        const section = document.getElementById('convertSection').value;
        const rack = document.getElementById('convertRack').value;
        const shelf = document.getElementById('convertShelf').value;

        try {
            const res = await apiFetch('/api/librarian/operations/acquisitions/convert-to-inventory', {
                method: 'POST',
                body: JSON.stringify({
                    requestId: parseInt(requestId),
                    category,
                    copies,
                    floor, block, section, rack, shelf
                })
            });
            if (res.success) {
                showToast(`Converted request #${requestId} into book #${res.data.bookId} with ${copies} copies!`, 'success');
                document.getElementById('convertAcqModal').classList.remove('show');
                loadAcquisitions();
            } else {
                showToast(res.error?.message || 'Conversion failed', 'error');
            }
        } catch (err) {
            showToast(err.message, 'error');
        }
    });
}

// ============================================================================
// 2. BULK CSV IMPORT
// ============================================================================
let currentCsvRows = [];

function setupCsvImport() {
    const fileInput = document.getElementById('csvFileInput');
    const dropZone = document.getElementById('csvDropZone');

    if (dropZone) {
        dropZone.addEventListener('dragover', (e) => { e.preventDefault(); dropZone.classList.add('drag-over'); });
        dropZone.addEventListener('dragleave', () => dropZone.classList.remove('drag-over'));
        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropZone.classList.remove('drag-over');
            if (e.dataTransfer.files.length > 0) {
                handleCsvFile(e.dataTransfer.files[0]);
            }
        });
    }

    if (fileInput) {
        fileInput.addEventListener('change', () => {
            if (fileInput.files.length > 0) {
                handleCsvFile(fileInput.files[0]);
            }
        });
    }

    document.getElementById('commitCsvBtn')?.addEventListener('click', async () => {
        if (!currentCsvRows || currentCsvRows.length === 0) {
            showToast('No parsed rows to import.', 'error');
            return;
        }

        const skipDuplicates = document.getElementById('skipDuplicatesCheck')?.checked || false;
        const commitBtn = document.getElementById('commitCsvBtn');
        commitBtn.disabled = true;
        commitBtn.textContent = 'Importing...';

        try {
            const res = await apiFetch('/api/librarian/operations/import-csv/commit', {
                method: 'POST',
                body: JSON.stringify({ rows: currentCsvRows, skipDuplicates })
            });

            if (res.success) {
                showToast(`Import Success! Created ${res.data.importedCount} books (${res.data.skippedCount} skipped duplicates).`, 'success');
                document.getElementById('csvPreviewContainer').style.display = 'none';
                currentCsvRows = [];
                if (fileInput) fileInput.value = '';
            } else {
                showToast(res.error?.message || 'Import failed.', 'error');
            }
        } catch (err) {
            showToast(err.message, 'error');
        } finally {
            commitBtn.disabled = false;
            commitBtn.textContent = 'Commit Import';
        }
    });
}

function handleCsvFile(file) {
    if (!file.name.endsWith('.csv') && file.type !== 'text/csv') {
        showToast('Please upload a valid .csv file.', 'error');
        return;
    }

    const reader = new FileReader();
    reader.onload = async (e) => {
        const text = e.target.result;
        try {
            const res = await apiFetch('/api/librarian/operations/import-csv/preview', {
                method: 'POST',
                body: JSON.stringify({ csvContent: text })
            });

            if (res.success) {
                currentCsvRows = res.data;
                renderCsvPreview(res.data);
            } else {
                showToast(res.error?.message || 'Failed to preview CSV', 'error');
            }
        } catch (err) {
            showToast(err.message, 'error');
        }
    };
    reader.readAsText(file);
}

function renderCsvPreview(rows) {
    const container = document.getElementById('csvPreviewContainer');
    const tbody = document.getElementById('csvPreviewBody');
    const countSpan = document.getElementById('csvRowCountBadge');
    if (!container || !tbody) return;

    container.style.display = 'block';
    if (countSpan) countSpan.textContent = `${rows.length} rows parsed`;

    let validCount = 0;
    let warningCount = 0;
    let invalidCount = 0;

    tbody.innerHTML = rows.map(r => {
        if (r.status === 'VALID') validCount++;
        else if (r.status === 'WARNING_DUPLICATE_ISBN') warningCount++;
        else invalidCount++;

        let statusBadge = '<span class="status-pill status-pill-available">Valid</span>';
        if (r.status === 'WARNING_DUPLICATE_ISBN') {
            statusBadge = '<span class="status-pill" style="background: rgba(245,166,35,0.15); color: #F5A623;">Duplicate ISBN</span>';
        } else if (r.status === 'INVALID') {
            statusBadge = '<span class="status-pill status-pill-lost">Invalid</span>';
        }

        return `
            <tr style="${r.status === 'INVALID' ? 'background: rgba(224,32,32,0.04);' : ''}">
                <td>${r.rowNumber}</td>
                <td><strong>${escapeHtml(r.title)}</strong></td>
                <td>${escapeHtml(r.author)}</td>
                <td><code>${escapeHtml(r.isbn || '')}</code></td>
                <td>${escapeHtml(r.category || 'General')}</td>
                <td>${r.copies}</td>
                <td>${escapeHtml(r.floor || '')} / ${escapeHtml(r.shelf || '')}</td>
                <td>${statusBadge}</td>
                <td style="font-size: 11px; color: ${r.status === 'INVALID' ? '#E02020' : '#888'};">${escapeHtml(r.errorMessage || '')}</td>
            </tr>
        `;
    }).join('');

    const statsElem = document.getElementById('csvStatsSummary');
    if (statsElem) {
        statsElem.innerHTML = `
            <span style="color: #008770; font-weight: bold;">✔ ${validCount} Valid</span> &nbsp;•&nbsp; 
            <span style="color: #F5A623; font-weight: bold;">⚠ ${warningCount} Duplicates</span> &nbsp;•&nbsp; 
            <span style="color: #E02020; font-weight: bold;">✕ ${invalidCount} Invalid</span>
        `;
    }

    const commitBtn = document.getElementById('commitCsvBtn');
    if (commitBtn) {
        commitBtn.disabled = (invalidCount > 0);
        if (invalidCount > 0) {
            commitBtn.title = 'Resolve invalid rows in CSV before committing.';
        } else {
            commitBtn.title = '';
        }
    }
}

// ============================================================================
// 3. QR / BARCODE SCANNER & LABELS
// ============================================================================
function setupBarcodeScanner() {
    const startCamBtn = document.getElementById('startCameraBtn');
    const stopCamBtn = document.getElementById('stopCameraBtn');
    const manualLookupBtn = document.getElementById('manualCodeLookupBtn');
    const manualInput = document.getElementById('manualCodeInput');

    if (startCamBtn) {
        startCamBtn.addEventListener('click', startCameraScanner);
    }
    if (stopCamBtn) {
        stopCamBtn.addEventListener('click', stopCameraScanner);
    }
    if (manualLookupBtn && manualInput) {
        manualLookupBtn.addEventListener('click', () => {
            const code = manualInput.value.trim();
            if (code) lookupScannedCopy(code);
        });
        manualInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                const code = manualInput.value.trim();
                if (code) lookupScannedCopy(code);
            }
        });
    }

    document.getElementById('printLabelsModalBtn')?.addEventListener('click', openPrintLabelsModal);
}

async function startCameraScanner() {
    const video = document.getElementById('scannerVideo');
    if (!video) return;

    if (!('BarcodeDetector' in window)) {
        showToast('BarcodeDetector API is not supported in this browser. Please use manual entry.', 'warning');
        return;
    }

    try {
        videoStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
        video.srcObject = videoStream;
        video.style.display = 'block';
        document.getElementById('startCameraBtn').style.display = 'none';
        document.getElementById('stopCameraBtn').style.display = 'inline-block';

        const detector = new window.BarcodeDetector({ formats: ['qr_code', 'code_128', 'ean_13'] });

        const scanFrame = async () => {
            if (!videoStream) return;
            try {
                const barcodes = await detector.detect(video);
                if (barcodes.length > 0) {
                    const val = barcodes[0].rawValue;
                    stopCameraScanner();
                    lookupScannedCopy(val);
                    return;
                }
            } catch (ignored) {}
            requestAnimationFrame(scanFrame);
        };
        requestAnimationFrame(scanFrame);
    } catch (err) {
        showToast('Camera access denied or unavailable: ' + err.message, 'error');
    }
}

function stopCameraScanner() {
    if (videoStream) {
        videoStream.getTracks().forEach(t => t.stop());
        videoStream = null;
    }
    const video = document.getElementById('scannerVideo');
    if (video) video.style.display = 'none';
    const startBtn = document.getElementById('startCameraBtn');
    const stopBtn = document.getElementById('stopCameraBtn');
    if (startBtn) startBtn.style.display = 'inline-block';
    if (stopBtn) stopBtn.style.display = 'none';
}

export async function lookupScannedCopy(code) {
    const card = document.getElementById('scannedCopyCard');
    if (!card) return;

    card.innerHTML = '<div style="padding: 24px; text-align: center;">Looking up copy ' + escapeHtml(code) + '...</div>';
    card.style.display = 'block';

    try {
        const res = await apiFetch(`/api/librarian/operations/copy-lookup?code=${encodeURIComponent(code)}`);
        if (!res.success || !res.data) {
            card.innerHTML = `<div style="padding: 16px; color: #E02020; font-weight: bold;">✕ No physical copy found matching: "${escapeHtml(code)}"</div>`;
            return;
        }

        const c = res.data;
        const isBorrowed = (c.status === 'BORROWED' && c.activeTransactionId);

        card.innerHTML = `
            <div style="background: white; border-radius: 8px; border: 1px solid var(--border-color); padding: 18px; box-shadow: 0 2px 8px rgba(0,0,0,0.05);">
                <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px;">
                    <div>
                        <h3 style="font-size: 18px; color: var(--navy-primary); font-weight: bold; margin-bottom: 4px;">${escapeHtml(c.title)}</h3>
                        <div style="font-size: 13px; color: var(--navy-light);">by ${escapeHtml(c.author)} • Category: ${escapeHtml(c.category)}</div>
                    </div>
                    <span class="status-pill status-pill-${(c.status || '').toLowerCase().replace('_', '-')}">${escapeHtml(c.status)}</span>
                </div>
                
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; font-size: 13px; background: #f8fafc; padding: 12px; border-radius: 6px; margin-bottom: 16px;">
                    <div><strong>Copy Code:</strong> <code>${escapeHtml(c.copyCode)}</code></div>
                    <div><strong>Barcode:</strong> <code>${escapeHtml(c.barcode || c.copyCode)}</code></div>
                    <div><strong>Location:</strong> Floor ${escapeHtml(c.floor || '1')}, Rack ${escapeHtml(c.rack || 'R1')}, Shelf ${escapeHtml(c.shelf || 'S1')}</div>
                    ${isBorrowed ? `
                        <div style="color: #E02020;"><strong>Current Borrower:</strong> ${escapeHtml(c.borrowerName)} (${escapeHtml(c.borrowerRegNo)})</div>
                        <div style="color: #E02020;"><strong>Due Date:</strong> ${escapeHtml(c.dueDate)} (Tx #${c.activeTransactionId})</div>
                    ` : ''}
                </div>

                <div style="display: flex; gap: 8px;">
                    ${c.status === 'AVAILABLE' || c.status === 'ALLOCATED' ? `
                        <button class="btn-quick btn-quick-primary" onclick="window.librarianCirculation.openIssueModal('${escapeHtml(c.copyCode)}')">
                            Issue This Book
                        </button>
                    ` : ''}
                    ${isBorrowed ? `
                        <button class="btn-quick btn-quick-primary" onclick="window.librarianCirculation.openReturnModal(${c.activeTransactionId})">
                            Check In / Return Book
                        </button>
                    ` : ''}
                    <button class="btn-quick" onclick="window.librarianInventory.openShelfModal(${c.bookId}, '${escapeHtml(c.title)}')">
                        Shelf Locator
                    </button>
                </div>
            </div>
        `;
    } catch (err) {
        card.innerHTML = `<div style="padding: 16px; color: #E02020;">Error looking up copy: ${escapeHtml(err.message)}</div>`;
    }
}

function openPrintLabelsModal() {
    const modal = document.getElementById('printLabelsModal');
    if (!modal) return;
    modal.classList.add('show');
}

// ============================================================================
// 4. DUE-DATE CALENDAR
// ============================================================================
function setupDueCalendar() {
    document.getElementById('calPrevMonthBtn')?.addEventListener('click', () => {
        currentMonth--;
        if (currentMonth < 1) {
            currentMonth = 12;
            currentYear--;
        }
        loadDueCalendar(currentYear, currentMonth);
    });

    document.getElementById('calNextMonthBtn')?.addEventListener('click', () => {
        currentMonth++;
        if (currentMonth > 12) {
            currentMonth = 1;
            currentYear++;
        }
        loadDueCalendar(currentYear, currentMonth);
    });
}

export async function loadDueCalendar(year = currentYear, month = currentMonth) {
    const titleElem = document.getElementById('calMonthTitle');
    const gridElem = document.getElementById('calendarDaysGrid');
    if (!gridElem) return;

    const monthNames = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
    if (titleElem) titleElem.textContent = `${monthNames[month - 1]} ${year}`;

    gridElem.innerHTML = '<div style="grid-column: 1 / -1; padding: 24px; text-align: center;">Loading loan due calendar...</div>';

    try {
        const res = await apiFetch(`/api/librarian/circulation/calendar?year=${year}&month=${month}`);
        const dateMap = {};
        if (res.success && res.data) {
            res.data.forEach(d => {
                dateMap[d.date] = d;
            });
        }

        renderCalendarGrid(year, month, dateMap);
    } catch (err) {
        gridElem.innerHTML = `<div style="grid-column: 1 / -1; padding: 24px; text-align: center; color: #E02020;">Failed to load calendar: ${escapeHtml(err.message)}</div>`;
    }
}

function renderCalendarGrid(year, month, dateMap) {
    const gridElem = document.getElementById('calendarDaysGrid');
    if (!gridElem) return;

    const firstDay = new Date(year, month - 1, 1).getDay();
    const daysInMonth = new Date(year, month, 0).getDate();
    const todayStr = new Date().toISOString().split('T')[0];

    let html = '';
    // Empty cells before start
    for (let i = 0; i < firstDay; i++) {
        html += '<div class="calendar-day empty"></div>';
    }

    for (let day = 1; day <= daysInMonth; day++) {
        const dStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
        const item = dateMap[dStr];
        const isToday = (dStr === todayStr);
        const hasDue = item && item.totalDue > 0;
        const hasOverdue = item && item.overdueCount > 0;

        html += `
            <div class="calendar-day ${isToday ? 'today' : ''} ${hasDue ? 'has-due' : ''} ${hasOverdue ? 'has-overdue' : ''}" 
                 onclick="window.librarianOperations.filterCirculationByDate('${dStr}')" title="Click to view loans due on ${dStr}">
                <div class="day-number">${day}</div>
                ${hasDue ? `
                    <div class="day-badge due-badge">${item.totalDue} due</div>
                ` : ''}
                ${hasOverdue ? `
                    <div class="day-badge overdue-badge">${item.overdueCount} overdue</div>
                ` : ''}
            </div>
        `;
    }

    gridElem.innerHTML = html;
}

export function filterCirculationByDate(dateStr) {
    window.location.hash = '#circulation';
    showToast(`Filtering circulation desk loans due on ${dateStr}`, 'info');
    if (window.librarianCirculation && window.librarianCirculation.loadActiveLoansForDueDate) {
        window.librarianCirculation.loadActiveLoansForDueDate(dateStr);
    }
}

// ============================================================================
// 5. STUDENT QUERIES
// ============================================================================
export async function loadStudentQueries() {
    const status = document.getElementById('queryStatusFilter')?.value || 'ALL';
    const tbody = document.getElementById('queriesTableBody');
    if (!tbody) return;

    tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px;">Loading student inquiries...</td></tr>';

    try {
        const res = await apiFetch(`/api/librarian/operations/queries?status=${encodeURIComponent(status)}`);
        if (!res.success || !res.data || res.data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px; color: var(--navy-light);">No inquiries found.</td></tr>';
            return;
        }

        tbody.innerHTML = res.data.map(q => `
            <tr>
                <td><strong>#${q.queryId}</strong></td>
                <td>
                    <div style="font-weight: 600; color: var(--navy-primary);">${escapeHtml(q.subject)}</div>
                    <div style="font-size: 11px; color: var(--navy-light); max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${escapeHtml(q.message)}</div>
                </td>
                <td><span class="badge" style="background: rgba(10,37,64,0.08);">${escapeHtml(q.category)}</span></td>
                <td>
                    <div>${escapeHtml(q.studentName)}</div>
                    <div style="font-size: 11px; color: var(--navy-light);">${escapeHtml(q.studentRegNo)}</div>
                </td>
                <td>${q.status === 'OPEN' ? '<span class="status-pill status-pill-under-review">Open</span>' : '<span class="status-pill status-pill-available">Resolved</span>'}</td>
                <td style="font-size: 11px; color: var(--navy-light);">${q.createdAt ? q.createdAt.substring(0, 16) : ''}</td>
                <td>
                    ${q.status === 'OPEN' ? `
                        <button class="btn-quick btn-quick-primary" style="padding: 3px 8px; font-size: 11px;" onclick="window.librarianOperations.openReplyModal(${q.queryId}, '${escapeHtml(q.studentName)}', '${escapeHtml(q.subject)}', '${escapeHtml(q.message)}')">
                            Reply
                        </button>
                    ` : `
                        <button class="btn-quick" style="padding: 3px 8px; font-size: 11px;" onclick="window.librarianOperations.viewReplyModal('${escapeHtml(q.subject)}', '${escapeHtml(q.message)}', '${escapeHtml(q.responseText || '')}', '${escapeHtml(q.resolverName || 'Staff')}')">
                            View Reply
                        </button>
                    `}
                </td>
            </tr>
        `).join('');
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #E02020; padding: 24px;">Failed to load queries: ${escapeHtml(err.message)}</td></tr>`;
    }
}

function setupStudentQueries() {
    document.getElementById('queryStatusFilter')?.addEventListener('change', loadStudentQueries);

    document.getElementById('replyQueryForm')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const queryId = document.getElementById('replyQueryId').value;
        const responseText = document.getElementById('replyQueryText').value;

        try {
            const res = await apiFetch('/api/librarian/operations/queries/reply', {
                method: 'POST',
                body: JSON.stringify({ queryId: parseInt(queryId), responseText })
            });
            if (res.success) {
                showToast(`Reply sent to student. Inquiry #${queryId} marked as Resolved.`, 'success');
                document.getElementById('replyQueryModal').classList.remove('show');
                loadStudentQueries();
            } else {
                showToast(res.error?.message || 'Failed to submit reply', 'error');
            }
        } catch (err) {
            showToast(err.message, 'error');
        }
    });
}

// ============================================================================
// 6. PURCHASES
// ============================================================================
export async function loadPurchases() {
    const tbody = document.getElementById('purchasesTableBody');
    if (!tbody) return;

    tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; padding: 24px;">Loading purchase records...</td></tr>';

    try {
        const [listRes, summaryRes] = await Promise.all([
            apiFetch('/api/librarian/operations/purchases'),
            apiFetch('/api/librarian/operations/purchases/summary')
        ]);

        if (summaryRes.success && summaryRes.data) {
            const s = summaryRes.data;
            document.getElementById('purchaseTotalSpend').textContent = `Rs. ${Number(s.totalExpenditure || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
            document.getElementById('purchaseTotalItems').textContent = Number(s.totalItems || 0).toLocaleString('en-IN');
            document.getElementById('purchaseTotalInvoices').textContent = Number(s.totalInvoices || 0).toLocaleString('en-IN');
        }

        if (!listRes.success || !listRes.data || listRes.data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; padding: 24px; color: var(--navy-light);">No purchase invoices recorded yet.</td></tr>';
            return;
        }

        tbody.innerHTML = listRes.data.map(p => `
            <tr>
                <td><strong>#${p.purchaseId}</strong></td>
                <td><strong>${escapeHtml(p.invoiceNo)}</strong></td>
                <td>${escapeHtml(p.supplierName)}</td>
                <td>${escapeHtml(p.purchaseDate ? p.purchaseDate.toString() : '')}</td>
                <td>Rs. ${Number(p.unitPrice).toFixed(2)}</td>
                <td><strong>${p.quantity}</strong></td>
                <td style="font-weight: bold; color: var(--navy-primary);">Rs. ${Number(p.totalAmount).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</td>
                <td style="font-size: 11px; color: var(--navy-light);">${escapeHtml(p.remarks || 'Standard Acquisition')}</td>
            </tr>
        `).join('');
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: #E02020; padding: 24px;">Failed to load purchases: ${escapeHtml(err.message)}</td></tr>`;
    }
}

function setupPurchases() {
    document.getElementById('addPurchaseForm')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const supplierName = document.getElementById('purSupplier').value;
        const invoiceNo = document.getElementById('purInvoice').value;
        const purchaseDate = document.getElementById('purDate').value;
        const unitPrice = document.getElementById('purUnitPrice').value;
        const quantity = parseInt(document.getElementById('purQuantity').value);
        const remarks = document.getElementById('purRemarks').value;

        try {
            const res = await apiFetch('/api/librarian/operations/purchases', {
                method: 'POST',
                body: JSON.stringify({ supplierName, invoiceNo, purchaseDate, unitPrice, quantity, remarks })
            });

            if (res.success) {
                showToast(`Purchase invoice #${invoiceNo} recorded successfully!`, 'success');
                document.getElementById('addPurchaseModal').classList.remove('show');
                loadPurchases();
            } else {
                showToast(res.error?.message || 'Failed to record purchase', 'error');
            }
        } catch (err) {
            showToast(err.message, 'error');
        }
    });

    // Auto-compute total in modal
    const unitPriceInput = document.getElementById('purUnitPrice');
    const quantityInput = document.getElementById('purQuantity');
    const totalPreview = document.getElementById('purTotalPreview');

    const updateTotal = () => {
        const u = parseFloat(unitPriceInput?.value) || 0;
        const q = parseInt(quantityInput?.value) || 0;
        if (totalPreview) totalPreview.textContent = `Rs. ${(u * q).toFixed(2)}`;
    };

    unitPriceInput?.addEventListener('input', updateTotal);
    quantityInput?.addEventListener('input', updateTotal);
}

// Modal helper bridges for window attachment
window.librarianOperations = {
    openStatusModal: (id, targetStatus) => {
        document.getElementById('acqStatusRequestId').value = id;
        document.getElementById('acqStatusTarget').value = targetStatus;
        document.getElementById('acqTargetStatusName').textContent = targetStatus;
        document.getElementById('acqStatusModal').classList.add('show');
    },
    openConvertModal: (id, title, author, isbn, dept, count) => {
        document.getElementById('convertAcqId').value = id;
        document.getElementById('convertAcqTitle').textContent = title;
        document.getElementById('convertCategory').value = dept || 'General';
        document.getElementById('convertCopies').value = Math.max(1, count);
        document.getElementById('convertAcqModal').classList.add('show');
    },
    openReplyModal: (id, studentName, subject, message) => {
        document.getElementById('replyQueryId').value = id;
        document.getElementById('replyStudentName').textContent = studentName;
        document.getElementById('replySubject').textContent = subject;
        document.getElementById('replyMessage').textContent = message;
        document.getElementById('replyQueryText').value = '';
        document.getElementById('replyQueryModal').classList.add('show');
    },
    viewReplyModal: (subject, message, reply, resolver) => {
        document.getElementById('viewQuerySubject').textContent = subject;
        document.getElementById('viewQueryMessage').textContent = message;
        document.getElementById('viewQueryReply').textContent = reply || 'No response recorded.';
        document.getElementById('viewQueryResolver').textContent = resolver;
        document.getElementById('viewQueryModal').classList.add('show');
    },
    filterCirculationByDate,
    loadFloorHeatmap,
    loadHighDemandAlerts
};

export async function loadFloorHeatmap() {
    const floorSelect = document.getElementById('heatmapFloorSelect');
    const gridContainer = document.getElementById('heatmapGridContainer');
    if (!gridContainer) return;

    const floor = floorSelect?.value || 'Floor 1';
    gridContainer.innerHTML = '<div style="grid-column: 1 / -1; text-align: center; padding: 40px; color: var(--navy-light);">Loading 2D floor layout & circulation density...</div>';

    try {
        const res = await apiFetch(`/api/librarian/layout/heatmap?floor=${encodeURIComponent(floor)}`);
        if (res && res.success && Array.isArray(res.data)) {
            renderHeatmapGrid(res.data);
        } else {
            gridContainer.innerHTML = '<div style="grid-column: 1 / -1; text-align: center; padding: 40px; color: var(--navy-light);">No layout grid configured for ' + escapeHtml(floor) + '.</div>';
        }
    } catch (err) {
        gridContainer.innerHTML = `<div style="grid-column: 1 / -1; text-align: center; color: #B91C1C; padding: 30px;">Failed to load heatmap: ${escapeHtml(err.message)}</div>`;
    }
}

function renderHeatmapGrid(cells) {
    const gridContainer = document.getElementById('heatmapGridContainer');
    if (!gridContainer) return;

    if (!cells || cells.length === 0) {
        gridContainer.innerHTML = '<div style="grid-column: 1 / -1; text-align: center; padding: 40px; color: var(--navy-light);">No racks found.</div>';
        return;
    }

    gridContainer.innerHTML = cells.map(cell => {
        const density = cell.densityLevel || 'LOW';
        let badgeColor = '#159B50';
        let badgeBg = '#E8F5EE';
        let borderColor = '#A3E0BF';

        if (density === 'HIGH') {
            badgeColor = '#B91C1C';
            badgeBg = '#FEF2F2';
            borderColor = '#FCA5A5';
        } else if (density === 'MEDIUM') {
            badgeColor = '#B45309';
            badgeBg = '#FEF3C7';
            borderColor = '#FDE68A';
        }

        return `
            <div class="heatmap-rack-card" style="background: white; border: 2px solid ${borderColor}; border-radius: 8px; padding: 16px; position: relative; box-shadow: 0 1px 4px rgba(0,0,0,0.04);">
                <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px;">
                    <div>
                        <span style="font-weight: 700; font-size: 15px; color: var(--navy-primary);">${escapeHtml(cell.rack)}</span>
                        <span style="font-size: 12px; color: var(--navy-light); margin-left: 6px;">(${escapeHtml(cell.label || 'Rack')})</span>
                    </div>
                    <span style="background: ${badgeBg}; color: ${badgeColor}; border: 1px solid ${borderColor}; padding: 2px 8px; border-radius: 12px; font-size: 10px; font-weight: 700;">
                        ${density} ACTIVITY
                    </span>
                </div>
                <div style="font-size: 12px; color: var(--navy-light); margin-bottom: 12px;">
                    <div>${escapeHtml(cell.section)} • ${escapeHtml(cell.block)}</div>
                    <div style="font-weight: 500; color: #334155; margin-top: 2px;">${escapeHtml(cell.categoryHint || 'General Stack')}</div>
                </div>
                <div style="display: flex; justify-content: space-between; font-size: 12px; border-top: 1px dashed var(--border-color); padding-top: 8px;">
                    <div><span style="color: var(--navy-light);">Copies:</span> <strong>${cell.totalCopies || 0}</strong></div>
                    <div><span style="color: var(--navy-light);">Active Loans:</span> <strong>${cell.activeBorrowCount || 0}</strong></div>
                    <div><span style="color: var(--navy-light);">Utilization:</span> <strong>${cell.fillPercentage || 0}%</strong></div>
                </div>
            </div>
        `;
    }).join('');
}

export async function loadHighDemandAlerts() {
    const tbody = document.getElementById('highDemandTableBody');
    const countDisplay = document.getElementById('highDemandCount');
    if (!tbody) return;

    tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px;">Analyzing inventory demand & waitlists...</td></tr>';

    try {
        const res = await apiFetch('/api/librarian/operations/high-demand-alerts');
        if (res && res.success && Array.isArray(res.data)) {
            const alerts = res.data;
            if (countDisplay) countDisplay.textContent = `${alerts.length} Books Alerted`;

            if (alerts.length === 0) {
                tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px; color: #159B50;">All inventory circulation queues are operating within optimal capacity. Zero shortage alerts.</td></tr>';
                return;
            }

            tbody.innerHTML = alerts.map(a => `
                <tr>
                    <td><strong>#${a.bookId}</strong></td>
                    <td>
                        <div style="font-weight: 600; color: var(--navy-primary);">${escapeHtml(a.title)}</div>
                        <div style="font-size: 11px; color: var(--navy-light);">by ${escapeHtml(a.author)} • ISBN: ${escapeHtml(a.isbn || 'N/A')}</div>
                    </td>
                    <td>${escapeHtml(a.category)} &bull; ${escapeHtml(a.department)}</td>
                    <td><span style="font-weight: 700; color: #B91C1C; font-size: 14px;">${a.waitingCount} queued</span></td>
                    <td>
                        <strong>${a.availableCopies}</strong> / ${a.totalCopies} total
                    </td>
                    <td>
                        <span class="stock-pill stock-red">${a.shortageRatio}x</span>
                    </td>
                    <td>
                        <div style="font-size: 12px; color: #334155; margin-bottom: 4px;">${escapeHtml(a.recommendation)}</div>
                        <a href="#operations/acquisitions" class="btn-quick" style="padding: 2px 8px; font-size: 11px;">Create Requisition</a>
                    </td>
                </tr>
            `).join('');
        }
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #B91C1C; padding: 24px;">Failed to load alerts: ${escapeHtml(err.message)}</td></tr>`;
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
