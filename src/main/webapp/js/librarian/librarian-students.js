/**
 * PSG iTech Central Library - Librarian Portal Students Module
 * Manages Student Directory, Profile Audits, No-Dues Clearance, and Account Status Toggles.
 */

import { apiFetch, showToast } from './librarian-api.js';

let currentStudentPage = 1;
let currentStudentPageSize = 20;

export function initStudentsView() {
    loadStudents();

    const searchInput = document.getElementById('studentSearchInput');
    const filterSelect = document.getElementById('studentFilterSelect');

    const trigger = () => {
        currentStudentPage = 1;
        loadStudents();
    };

    if (searchInput) searchInput.addEventListener('input', debounce(trigger, 300));
    if (filterSelect) filterSelect.addEventListener('change', trigger);

    const toggleForm = document.getElementById('toggleStatusForm');
    if (toggleForm) toggleForm.addEventListener('submit', handleToggleStatusSubmit);
}

export async function loadStudents() {
    const q = document.getElementById('studentSearchInput')?.value || '';
    const filter = document.getElementById('studentFilterSelect')?.value || '';

    const tbody = document.getElementById('studentsTableBody');
    if (tbody) tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px;">Loading student records...</td></tr>';

    try {
        const params = new URLSearchParams({
            page: currentStudentPage,
            pageSize: currentStudentPageSize,
            q: q.trim(),
            filter: filter
        });

        const res = await apiFetch(`/api/librarian/students?${params.toString()}`);
        if (res && res.success && res.data) {
            renderStudentsTable(res.data, res.meta);
        }
    } catch (err) {
        if (tbody) tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #B91C1C; padding: 24px;">Failed to load students: ${escapeHtml(err.message)}</td></tr>`;
    }
}

function renderStudentsTable(students, meta) {
    const tbody = document.getElementById('studentsTableBody');
    if (!tbody) return;

    if (!students || students.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px; color: #5F6368;">No students match your filter.</td></tr>';
        renderPagination(0, 0, 0);
        return;
    }

    tbody.innerHTML = students.map(s => {
        const activeLoans = s.activeLoans || 0;
        const overdueLoans = s.overdueLoans || 0;
        const unpaidFines = parseFloat(s.unpaidFines || 0);

        let duesPill = '<span class="stock-pill stock-green">Clear</span>';
        if (overdueLoans > 0 || unpaidFines > 0) {
            duesPill = `<span class="stock-pill stock-red">Dues: ₹${unpaidFines.toFixed(2)} (${overdueLoans} overdue)</span>`;
        } else if (activeLoans > 0) {
            duesPill = `<span class="stock-pill stock-amber">${activeLoans} Active Loans</span>`;
        }

        return `
            <tr>
                <td><strong>${escapeHtml(s.registerNumber || '--')}</strong></td>
                <td>
                    <div style="font-weight: 600; color: #203B60;">${escapeHtml(s.name)}</div>
                    <div style="font-size: 11px; color: #5F6368;">${escapeHtml(s.email)}</div>
                </td>
                <td>${escapeHtml(s.department || '--')} (Yr ${s.year || '--'})</td>
                <td>
                    <span class="stock-pill ${s.active ? 'stock-green' : 'stock-red'}">${s.active ? 'ACTIVE' : 'INACTIVE'}</span>
                </td>
                <td>${duesPill}</td>
                <td style="white-space: nowrap;">
                    <button class="btn-quick" style="padding: 4px 8px; font-size: 11px;" onclick="window.librarianStudents.viewStudentProfile(${s.userId})">Profile</button>
                    <button class="btn-quick" style="padding: 4px 8px; font-size: 11px; color: #159B50; border-color: #159B50;" onclick="window.librarianStudents.checkNoDues(${s.userId})">No-Dues</button>
                    <button class="btn-quick" style="padding: 4px 8px; font-size: 11px; color: #B45309; border-color: #B45309;" onclick="window.librarianStudents.openToggleModal(${s.userId}, '${escapeHtml(s.name)}', ${s.active})">Status</button>
                </td>
            </tr>
        `;
    }).join('');

    if (meta) {
        renderPagination(meta.page, meta.pageSize, meta.total);
    }
}

function renderPagination(page, pageSize, total) {
    const info = document.getElementById('studentPaginationInfo');
    const prevBtn = document.getElementById('studentPrevBtn');
    const nextBtn = document.getElementById('studentNextBtn');

    if (info) {
        const start = total === 0 ? 0 : (page - 1) * pageSize + 1;
        const end = Math.min(page * pageSize, total);
        info.innerText = `Showing ${start}-${end} of ${total} students`;
    }

    if (prevBtn) {
        prevBtn.disabled = page <= 1;
        prevBtn.onclick = () => { if (currentStudentPage > 1) { currentStudentPage--; loadStudents(); } };
    }
    if (nextBtn) {
        const totalPages = Math.ceil(total / pageSize);
        nextBtn.disabled = page >= totalPages;
        nextBtn.onclick = () => { if (currentStudentPage < totalPages) { currentStudentPage++; loadStudents(); } };
    }
}

export async function viewStudentProfile(studentId) {
    try {
        const res = await apiFetch(`/api/librarian/students/profile?studentId=${studentId}`);
        if (res && res.success && res.data) {
            const data = res.data;
            const loans = data.activeLoans || [];
            const history = data.borrowingHistory || [];
            const fines = data.fines || [];

            const body = document.getElementById('studentProfileModalBody');
            if (body) {
                body.innerHTML = `
                    <div style="margin-bottom: 16px;">
                        <h4 style="font-size: 15px; color: #203B60; margin-bottom: 8px;">Active Loans (${loans.length})</h4>
                        ${loans.length === 0 ? '<p style="color: #5F6368; font-size: 13px;">No active loans.</p>' : `
                            <ul style="padding-left: 18px; font-size: 13px;">
                                ${loans.map(l => `<li><strong>${escapeHtml(l.bookTitle)}</strong> (Due: ${l.dueDate})</li>`).join('')}
                            </ul>
                        `}
                    </div>
                    <div style="margin-bottom: 16px;">
                        <h4 style="font-size: 15px; color: #203B60; margin-bottom: 8px;">Outstanding Fines</h4>
                        ${fines.length === 0 ? '<p style="color: #5F6368; font-size: 13px;">Zero outstanding fines.</p>' : `
                            <ul style="padding-left: 18px; font-size: 13px;">
                                ${fines.filter(f => f.status !== 'PAID').map(f => `<li>Fine #${f.fineId}: ₹${f.outstandingAmount} (${f.status})</li>`).join('')}
                            </ul>
                        `}
                    </div>
                    <div>
                        <h4 style="font-size: 15px; color: #203B60; margin-bottom: 8px;">Past Borrowing History (${history.length})</h4>
                        <p style="color: #5F6368; font-size: 12px;">Total completed transactions on record: ${history.length}</p>
                    </div>
                `;
            }
            openModal('studentProfileModal');
        }
    } catch (err) {
        showToast('Failed to load profile: ' + err.message, 'error');
    }
}

export async function checkNoDues(studentId) {
    try {
        const res = await apiFetch(`/api/librarian/students/no-dues?studentId=${studentId}`);
        if (res && res.success && res.data) {
            const slip = res.data;
            const area = document.getElementById('noDuesSlipArea');
            if (area) {
                area.innerHTML = `
                    <div style="text-align: center; border-bottom: 2px solid #203B60; padding-bottom: 12px; margin-bottom: 16px;">
                        <h2 style="color: #203B60; font-family: 'Times New Roman', serif; font-size: 22px;">PSG INSTITUTE OF TECHNOLOGY AND APPLIED RESEARCH</h2>
                        <h3 style="font-size: 16px; color: #5F6368;">CENTRAL LIBRARY • CLEARANCE CERTIFICATE (NO-DUES)</h3>
                    </div>
                    <table style="width: 100%; font-size: 13px; margin-bottom: 20px;">
                        <tr><td style="padding: 4px 0;"><strong>Student Name:</strong> ${escapeHtml(slip.studentName)}</td><td style="padding: 4px 0;"><strong>Roll Number:</strong> ${escapeHtml(slip.registerNumber)}</td></tr>
                        <tr><td style="padding: 4px 0;"><strong>Department:</strong> ${escapeHtml(slip.department)}</td><td style="padding: 4px 0;"><strong>Academic Year:</strong> ${slip.year}</td></tr>
                        <tr><td style="padding: 4px 0;"><strong>Certificate Ref:</strong> ${slip.certificateReference}</td><td style="padding: 4px 0;"><strong>Date & Time:</strong> ${slip.issuedTimestamp}</td></tr>
                    </table>
                    <div style="background: #F8F6F0; border: 1px solid #E2DDD5; padding: 16px; border-radius: 4px; margin-bottom: 24px;">
                        <div style="display: flex; justify-content: space-between; margin-bottom: 8px;"><span>Active Borrowings / Held Books:</span> <strong>${slip.activeLoansCount}</strong></div>
                        <div style="display: flex; justify-content: space-between; margin-bottom: 8px;"><span>Pending / Unpaid Fines:</span> <strong>₹${slip.unpaidFinesAmount}</strong></div>
                        <div style="display: flex; justify-content: space-between;"><span>Pending Interchange Transfers:</span> <strong>${slip.pendingTransfersCount}</strong></div>
                    </div>
                    <div style="text-align: center; padding: 14px; border: 2px solid ${slip.eligible ? '#159B50' : '#B91C1C'}; border-radius: 4px; margin-bottom: 24px;">
                        <h3 style="color: ${slip.eligible ? '#159B50' : '#B91C1C'}; font-size: 18px; font-weight: bold;">
                            ${slip.eligible ? '✓ LIBRARY CLEARANCE GRANTED (NO DUES PENDING)' : '✗ CLEARANCE REJECTED (OUTSTANDING DUES)'}
                        </h3>
                    </div>
                    <div style="display: flex; justify-content: space-between; margin-top: 40px; font-size: 12px; color: #5F6368;">
                        <div>Verified by: ${escapeHtml(slip.issuedByStaffName)} (Staff ID: ${slip.issuedByStaffId})</div>
                        <div style="text-align: right; border-top: 1px solid #5F6368; padding-top: 4px; width: 200px;">Librarian Authorized Seal / Signature</div>
                    </div>
                `;
            }
            openModal('noDuesModal');
        }
    } catch (err) {
        showToast('Failed to check No-Dues clearance: ' + err.message, 'error');
    }
}

export function openToggleModal(studentId, name, currentActive) {
    document.getElementById('toggleStudentId').value = studentId;
    document.getElementById('toggleTargetActive').value = (!currentActive).toString();
    document.getElementById('toggleStudentName').innerText = name;
    document.getElementById('toggleActionText').innerText = currentActive ? 'DEACTIVATE' : 'ACTIVATE';
    document.getElementById('toggleReasonInput').value = '';
    openModal('toggleStatusModal');
}

export async function handleToggleStatusSubmit(e) {
    e.preventDefault();
    const form = e.target;
    const studentId = parseInt(form.studentId.value);
    const active = form.targetActive.value === 'true';
    const reason = form.reason.value.trim();

    try {
        await apiFetch('/api/librarian/students/toggle-status', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ studentId, active, reason })
        });
        showToast(`Student account status changed to ${active ? 'ACTIVE' : 'INACTIVE'}.`, 'success');
        closeModal('toggleStatusModal');
        loadStudents();
    } catch (err) {
        showToast('Failed to toggle status: ' + err.message, 'error');
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

window.librarianStudents = {
    viewStudentProfile,
    checkNoDues,
    openToggleModal
};
