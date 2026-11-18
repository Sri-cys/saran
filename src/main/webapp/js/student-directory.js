/**
 * PSG iTech Library - Student Master Directory Controller
 * Handles live search, multi-factor filtering, pagination,
 * student profile inspection, and peer-to-peer book interchange initiation.
 */

(function () {
    'use strict';

    let state = {
        page: 1,
        pageSize: 20,
        query: '',
        registrationStatus: 'ALL',
        accountStatus: 'ALL',
        totalRecords: 0,
        totalPages: 1,
        currentViewer: null,
        selectedStudent: null,
        userActiveHoldings: []
    };

    let searchTimeout = null;

    // DOM Elements
    const searchInput = document.getElementById('searchInput');
    const filterRegistration = document.getElementById('filterRegistration');
    const filterAccount = document.getElementById('filterAccount');
    const filterPageSize = document.getElementById('filterPageSize');
    const btnClearFilters = document.getElementById('btnClearFilters');
    const studentTableBody = document.getElementById('studentTableBody');
    const tableResultSummary = document.getElementById('tableResultSummary');
    const tablePaginationSummary = document.getElementById('tablePaginationSummary');
    const paginationInfo = document.getElementById('paginationInfo');
    const paginationButtons = document.getElementById('paginationButtons');

    // Metrics
    const metricTotal = document.getElementById('metricTotal');
    const metricRegistered = document.getElementById('metricRegistered');
    const metricUnregistered = document.getElementById('metricUnregistered');
    const metricActive = document.getElementById('metricActive');

    // Profile Modal
    const profileModal = document.getElementById('profileModal');
    const modalAvatar = document.getElementById('modalAvatar');
    const modalStudentName = document.getElementById('modalStudentName');
    const modalRollNumber = document.getElementById('modalRollNumber');
    const modalAccountBadge = document.getElementById('modalAccountBadge');
    const modalRegBadge = document.getElementById('modalRegBadge');
    const modalEmail = document.getElementById('modalEmail');
    const modalDeptYear = document.getElementById('modalDeptYear');
    const modalPhone = document.getElementById('modalPhone');
    const modalUserId = document.getElementById('modalUserId');
    const modalHoldingsCountBadge = document.getElementById('modalHoldingsCountBadge');
    const modalHoldingsList = document.getElementById('modalHoldingsList');
    const modalInterchangeSection = document.getElementById('modalInterchangeSection');
    const btnInitiatePeerHandover = document.getElementById('btnInitiatePeerHandover');

    // Peer Interchange Modal
    const peerInterchangeModal = document.getElementById('peerInterchangeModal');
    const handoverPeerName = document.getElementById('handoverPeerName');
    const handoverPeerRoll = document.getElementById('handoverPeerRoll');
    const handoverBookSelect = document.getElementById('handoverBookSelect');
    const handoverMessage = document.getElementById('handoverMessage');

    document.addEventListener('DOMContentLoaded', () => {
        initEvents();
        fetchViewerSession();
        fetchDirectory();
    });

    function initEvents() {
        if (searchInput) {
            searchInput.addEventListener('input', (e) => {
                clearTimeout(searchTimeout);
                searchTimeout = setTimeout(() => {
                    state.query = e.target.value.trim();
                    state.page = 1;
                    fetchDirectory();
                }, 300);
            });
        }

        if (filterRegistration) {
            filterRegistration.addEventListener('change', (e) => {
                state.registrationStatus = e.target.value;
                state.page = 1;
                fetchDirectory();
            });
        }

        if (filterAccount) {
            filterAccount.addEventListener('change', (e) => {
                state.accountStatus = e.target.value;
                state.page = 1;
                fetchDirectory();
            });
        }

        if (filterPageSize) {
            filterPageSize.addEventListener('change', (e) => {
                state.pageSize = parseInt(e.target.value, 10) || 20;
                state.page = 1;
                fetchDirectory();
            });
        }

        if (btnClearFilters) {
            btnClearFilters.addEventListener('click', () => {
                if (searchInput) searchInput.value = '';
                if (filterRegistration) filterRegistration.value = 'ALL';
                if (filterAccount) filterAccount.value = 'ALL';
                if (filterPageSize) filterPageSize.value = '20';

                state.query = '';
                state.registrationStatus = 'ALL';
                state.accountStatus = 'ALL';
                state.pageSize = 20;
                state.page = 1;
                fetchDirectory();
            });
        }

        if (btnInitiatePeerHandover) {
            btnInitiatePeerHandover.addEventListener('click', () => {
                if (!state.selectedStudent) return;
                openPeerInterchange(state.selectedStudent);
            });
        }
    }

    function fetchViewerSession() {
        fetch('profile', {
            headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
        })
        .then(res => {
            if (res.ok) return res.json();
            return null;
        })
        .then(data => {
            if (data && data.success && data.user) {
                state.currentViewer = data.user;
                updateAuthNavbar(data.user);
                if (data.user.role === 'STUDENT') {
                    fetchViewerHoldings();
                }
            }
        })
        .catch(() => {});
    }

    function updateAuthNavbar(user) {
        const authArea = document.getElementById('authNavArea');
        if (!authArea) return;

        let dashboardLink = 'student-home.html';
        if (user.role === 'LIBRARIAN') dashboardLink = 'librarian-dashboard.html';
        else if (user.role === 'ADMIN') dashboardLink = 'admin-users.html';

        authArea.innerHTML = `
            <div style="display: flex; align-items: center; gap: 12px;">
                <span class="text-secondary" style="font-size: 0.9rem;">
                    Signed in as <strong style="color: var(--primary);">${escapeHtml(user.name)}</strong> (${escapeHtml(user.role)})
                </span>
                <a href="${dashboardLink}" class="btn btn-primary btn-sm">My Portal</a>
                <a href="logout" class="btn btn-outline btn-sm" style="color: var(--danger); border-color: var(--border);">Logout</a>
            </div>
        `;
    }

    function fetchViewerHoldings() {
        fetch('borrow/student', {
            headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
        })
        .then(res => res.ok ? res.json() : null)
        .then(data => {
            if (data && data.success && Array.isArray(data.data)) {
                state.userActiveHoldings = data.data.filter(b => b.status === 'ACTIVE');
            }
        })
        .catch(() => {});
    }

    function fetchDirectory() {
        const params = new URLSearchParams({
            page: state.page,
            pageSize: state.pageSize
        });
        if (state.query) params.append('q', state.query);
        if (state.registrationStatus && state.registrationStatus !== 'ALL') {
            params.append('registrationStatus', state.registrationStatus);
        }
        if (state.accountStatus && state.accountStatus !== 'ALL') {
            params.append('accountStatus', state.accountStatus);
        }

        studentTableBody.innerHTML = `
            <tr>
                <td colspan="7" style="text-align: center; padding: 40px; color: var(--text-secondary);">
                    Loading verified student directory...
                </td>
            </tr>
        `;

        fetch('api/students/directory?' + params.toString(), {
            headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
        })
        .then(res => {
            if (!res.ok) throw new Error('HTTP ' + res.status);
            return res.json();
        })
        .then(data => {
            if (data && data.success) {
                renderMetrics(data.metrics);
                renderTable(data.students, data.pagination);
                renderPagination(data.pagination);
            } else {
                studentTableBody.innerHTML = `
                    <tr>
                        <td colspan="7" style="text-align: center; padding: 30px; color: var(--danger);">
                            ${escapeHtml(data.message || 'Failed to load student directory')}
                        </td>
                    </tr>
                `;
            }
        })
        .catch(err => {
            studentTableBody.innerHTML = `
                <tr>
                    <td colspan="7" style="text-align: center; padding: 30px; color: var(--danger);">
                        Error communicating with library directory service: ${escapeHtml(err.message)}
                    </td>
                </tr>
            `;
        });
    }

    function renderMetrics(metrics) {
        if (!metrics) return;
        if (metricTotal) metricTotal.textContent = metrics.total || '63';
        if (metricRegistered) metricRegistered.textContent = metrics.registered || '0';
        if (metricUnregistered) metricUnregistered.textContent = metrics.unregistered || '0';
        if (metricActive) metricActive.textContent = metrics.active || '63';
    }

    function renderTable(students, pagination) {
        if (!students || students.length === 0) {
            studentTableBody.innerHTML = `
                <tr>
                    <td colspan="7" style="text-align: center; padding: 40px; color: var(--text-secondary);">
                        No institutional student records match the specified filters.
                    </td>
                </tr>
            `;
            if (tableResultSummary) tableResultSummary.textContent = 'Verified Student Class List (0 Records)';
            if (tablePaginationSummary) tablePaginationSummary.textContent = 'Showing 0 of 0';
            if (paginationInfo) paginationInfo.textContent = 'Showing 0 entries';
            return;
        }

        const startIdx = (pagination.page - 1) * pagination.pageSize + 1;
        const endIdx = Math.min(startIdx + students.length - 1, pagination.totalRecords);

        if (tableResultSummary) {
            tableResultSummary.textContent = `Verified Student Class List (${pagination.totalRecords} Records)`;
        }
        if (tablePaginationSummary) {
            tablePaginationSummary.textContent = `Showing ${startIdx} - ${endIdx} of ${pagination.totalRecords}`;
        }
        if (paginationInfo) {
            paginationInfo.textContent = `Showing ${startIdx} to ${endIdx} of ${pagination.totalRecords} entries`;
        }

        let html = '';
        students.forEach((s, idx) => {
            const sNo = startIdx + idx;
            const isReg = s.registrationStatus === 'REGISTERED';
            const isActive = s.accountStatus === 'ACTIVE';

            const regBadge = isReg
                ? `<span style="background: #E8F8F0; color: #159B50; padding: 3px 8px; border-radius: 4px; font-size: 0.82rem; font-weight: bold; border: 1px solid #C4EED0;">REGISTERED</span>`
                : `<span style="background: #FEF3C7; color: #B45309; padding: 3px 8px; border-radius: 4px; font-size: 0.82rem; font-weight: bold; border: 1px solid #FCD34D;">NOT REGISTERED</span>`;

            const accBadge = isActive
                ? `<span style="background: #E8F8F0; color: #159B50; padding: 3px 8px; border-radius: 4px; font-size: 0.82rem; font-weight: bold; border: 1px solid #C4EED0;">ACTIVE</span>`
                : `<span style="background: #FDE8E8; color: #C0392B; padding: 3px 8px; border-radius: 4px; font-size: 0.82rem; font-weight: bold; border: 1px solid #F8B4B4;">INACTIVE</span>`;

            let deptYear = '<span class="text-secondary" style="font-size: 0.85rem;">Pending Account</span>';
            if (s.department) {
                deptYear = `<strong>${escapeHtml(s.department)}</strong>${s.year ? ` &bull; Year ${s.year}` : ''}`;
            }

            const canTransfer = state.currentViewer && state.currentViewer.role === 'STUDENT'
                && isReg && isActive && state.currentViewer.userId !== s.userId;

            html += `
                <tr style="border-bottom: 1px solid var(--border); transition: background-color 0.15s ease;" onmouseover="this.style.backgroundColor='#FDFCF9'" onmouseout="this.style.backgroundColor='transparent'">
                    <td style="padding: 12px 16px; font-weight: 500; color: var(--text-secondary);">${sNo}</td>
                    <td style="padding: 12px 16px;">
                        <code style="font-size: 0.95rem; font-weight: bold; color: var(--primary); background: #EEF2F6; padding: 2px 6px; border-radius: 4px;">
                            ${escapeHtml(s.permanentRollNumber)}
                        </code>
                    </td>
                    <td style="padding: 12px 16px;">
                        <span style="font-weight: bold; color: #111827; font-size: 0.98rem;">${escapeHtml(s.fullName)}</span>
                        ${s.email ? `<div class="text-secondary" style="font-size: 0.8rem; margin-top: 1px;">${escapeHtml(s.email)}</div>` : ''}
                    </td>
                    <td style="padding: 12px 16px;">${deptYear}</td>
                    <td style="padding: 12px 16px; text-align: center;">${accBadge}</td>
                    <td style="padding: 12px 16px; text-align: center;">${regBadge}</td>
                    <td style="padding: 12px 16px; text-align: center;">
                        <div style="display: flex; gap: 6px; justify-content: center; align-items: center;">
                            <button type="button" class="btn btn-outline btn-sm" onclick="window.studentDirectory.viewProfile('${escapeHtml(s.permanentRollNumber)}')" style="padding: 4px 10px; font-size: 0.82rem;">
                                Profile
                            </button>
                            ${canTransfer ? `
                                <button type="button" class="btn btn-primary btn-sm" onclick="window.studentDirectory.quickHandover('${escapeHtml(s.permanentRollNumber)}', '${escapeHtml(s.fullName)}')" style="padding: 4px 10px; font-size: 0.82rem;">
                                    Transfer &rarr;
                                </button>
                            ` : ''}
                        </div>
                    </td>
                </tr>
            `;
        });

        studentTableBody.innerHTML = html;
    }

    function renderPagination(pagination) {
        if (!paginationButtons) return;
        state.totalPages = pagination.totalPages;
        state.page = pagination.page;

        if (pagination.totalPages <= 1) {
            paginationButtons.innerHTML = '';
            return;
        }

        let buttons = '';

        // Previous button
        buttons += `
            <button type="button" class="btn btn-outline btn-sm" ${state.page <= 1 ? 'disabled style="opacity: 0.5; cursor: not-allowed;"' : ''} onclick="window.studentDirectory.changePage(${state.page - 1})">
                &laquo; Prev
            </button>
        `;

        for (let p = 1; p <= pagination.totalPages; p++) {
            const isActive = p === state.page;
            buttons += `
                <button type="button" class="btn ${isActive ? 'btn-primary' : 'btn-outline'} btn-sm" onclick="window.studentDirectory.changePage(${p})">
                    ${p}
                </button>
            `;
        }

        // Next button
        buttons += `
            <button type="button" class="btn btn-outline btn-sm" ${state.page >= pagination.totalPages ? 'disabled style="opacity: 0.5; cursor: not-allowed;"' : ''} onclick="window.studentDirectory.changePage(${state.page + 1})">
                Next &raquo;
            </button>
        `;

        paginationButtons.innerHTML = buttons;
    }

    function changePage(newPage) {
        if (newPage < 1 || newPage > state.totalPages || newPage === state.page) return;
        state.page = newPage;
        fetchDirectory();
        window.scrollTo({ top: 300, behavior: 'smooth' });
    }

    function viewProfile(rollNumber) {
        if (!rollNumber) return;

        fetch('api/students/profile?rollNumber=' + encodeURIComponent(rollNumber), {
            headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
        })
        .then(res => {
            if (!res.ok) throw new Error('Student record not found');
            return res.json();
        })
        .then(data => {
            if (data && data.success && data.student) {
                state.selectedStudent = data.student;
                populateProfileModal(data.student, data.activeBorrowings, data.canInitiateInterchange);
                if (profileModal) profileModal.style.display = 'flex';
            }
        })
        .catch(err => {
            alert('Failed to load student profile: ' + err.message);
        });
    }

    function populateProfileModal(student, holdings, canInitiateInterchange) {
        if (modalAvatar) modalAvatar.textContent = (student.fullName || 'S').charAt(0).toUpperCase();
        if (modalStudentName) modalStudentName.textContent = student.fullName || '--';
        if (modalRollNumber) modalRollNumber.textContent = student.permanentRollNumber || '--';

        const isReg = student.registrationStatus === 'REGISTERED';
        const isActive = student.accountStatus === 'ACTIVE';

        if (modalAccountBadge) {
            modalAccountBadge.textContent = student.accountStatus;
            modalAccountBadge.className = isActive ? 'badge badge-success' : 'badge badge-danger';
            modalAccountBadge.style.background = isActive ? '#E8F8F0' : '#FDE8E8';
            modalAccountBadge.style.color = isActive ? '#159B50' : '#C0392B';
        }

        if (modalRegBadge) {
            modalRegBadge.textContent = isReg ? 'REGISTERED ACCOUNT' : 'NOT REGISTERED';
            modalRegBadge.className = isReg ? 'badge badge-success' : 'badge badge-warning';
            modalRegBadge.style.background = isReg ? '#E8F8F0' : '#FEF3C7';
            modalRegBadge.style.color = isReg ? '#159B50' : '#B45309';
        }

        if (modalEmail) modalEmail.textContent = student.email || 'No email registered yet';
        if (modalDeptYear) {
            modalDeptYear.textContent = student.department ? `${student.department} (Year ${student.year || 1})` : 'Not configured';
        }
        if (modalPhone) modalPhone.textContent = student.phone || 'None provided';
        if (modalUserId) modalUserId.textContent = student.userId ? `User #${student.userId}` : 'Unlinked';

        // Render Active Borrowings
        if (modalHoldingsList) {
            if (!holdings || holdings.length === 0) {
                modalHoldingsList.innerHTML = `
                    <div style="padding: 16px; text-align: center; color: var(--text-secondary); background: #FAF9F6; border-radius: 4px; border: 1px dashed var(--border);">
                        No active physical books currently checked out by this student.
                    </div>
                `;
                if (modalHoldingsCountBadge) modalHoldingsCountBadge.textContent = '0 Books';
            } else {
                if (modalHoldingsCountBadge) modalHoldingsCountBadge.textContent = `${holdings.length} Active Book${holdings.length > 1 ? 's' : ''}`;
                let listHtml = '<div style="display: flex; flex-direction: column; gap: 10px;">';
                holdings.forEach(h => {
                    listHtml += `
                        <div style="padding: 12px 14px; background: #FFFFFF; border: 1px solid var(--border); border-left: 3px solid var(--primary); border-radius: 4px;">
                            <div style="font-weight: bold; color: var(--primary); font-size: 0.95rem;">
                                ${escapeHtml(h.bookTitle || 'Library Book')}
                            </div>
                            <div class="text-secondary" style="font-size: 0.85rem; margin-top: 2px;">
                                Author: ${escapeHtml(h.bookAuthor || '--')} &bull; Copy: <code>${escapeHtml(h.copyCode || '--')}</code>
                            </div>
                            <div style="margin-top: 6px; font-size: 0.82rem; display: flex; justify-content: space-between;">
                                <span>Issue Date: ${escapeHtml(h.issueDate || '--')}</span>
                                <span style="font-weight: bold; color: var(--primary);">Due Date: ${escapeHtml(h.dueDate || '--')}</span>
                            </div>
                        </div>
                    `;
                });
                listHtml += '</div>';
                modalHoldingsList.innerHTML = listHtml;
            }
        }

        // Show/hide Peer Handover section
        if (modalInterchangeSection) {
            if (canInitiateInterchange && state.userActiveHoldings.length > 0) {
                modalInterchangeSection.style.display = 'block';
            } else {
                modalInterchangeSection.style.display = 'none';
            }
        }
    }

    function closeProfileModal() {
        if (profileModal) profileModal.style.display = 'none';
        state.selectedStudent = null;
    }

    function quickHandover(rollNumber, studentName) {
        state.selectedStudent = {
            permanentRollNumber: rollNumber,
            fullName: studentName
        };
        openPeerInterchange(state.selectedStudent);
    }

    function openPeerInterchange(student) {
        if (!state.currentViewer || state.currentViewer.role !== 'STUDENT') {
            alert('Please sign in with your student account to initiate a peer book interchange.');
            return;
        }

        if (state.userActiveHoldings.length === 0) {
            alert('You do not currently have any active borrowed books to transfer.');
            return;
        }

        if (handoverPeerName) handoverPeerName.textContent = student.fullName || '--';
        if (handoverPeerRoll) handoverPeerRoll.textContent = student.permanentRollNumber || '--';

        if (handoverBookSelect) {
            let options = '<option value="" disabled selected>-- Select a book to transfer --</option>';
            state.userActiveHoldings.forEach(h => {
                options += `
                    <option value="${h.transactionId}">
                        ${escapeHtml(h.bookTitle || 'Book')} (Copy: ${escapeHtml(h.copyCode || '')}, Due: ${escapeHtml(h.dueDate || '')})
                    </option>
                `;
            });
            handoverBookSelect.innerHTML = options;
        }

        if (handoverMessage) handoverMessage.value = '';
        if (peerInterchangeModal) peerInterchangeModal.style.display = 'flex';
    }

    function closePeerInterchangeModal() {
        if (peerInterchangeModal) peerInterchangeModal.style.display = 'none';
    }

    function submitPeerHandover() {
        const txId = handoverBookSelect ? handoverBookSelect.value : null;
        if (!txId) {
            alert('Please select a borrowed book to transfer.');
            return;
        }

        if (!state.selectedStudent || !state.selectedStudent.permanentRollNumber) {
            alert('Recipient roll number missing.');
            return;
        }

        const btn = document.getElementById('btnSubmitHandover');
        if (btn) {
            btn.disabled = true;
            btn.textContent = 'Submitting...';
        }

        const note = handoverMessage ? handoverMessage.value.trim() : '';

        // Retrieve CSRF token if present
        let csrfToken = '';
        const metaCsrf = document.querySelector('meta[name="csrf-token"]');
        if (metaCsrf) csrfToken = metaCsrf.getAttribute('content');

        const formData = new URLSearchParams();
        formData.append('transactionId', txId);
        formData.append('recipientRegisterNumber', state.selectedStudent.permanentRollNumber);
        if (note) formData.append('requestNote', note);

        fetch('interchanges', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Requested-With': 'XMLHttpRequest',
                'X-CSRF-Token': csrfToken
            },
            body: formData.toString()
        })
        .then(res => res.json())
        .then(data => {
            if (data && data.success) {
                alert('Success! Transfer request has been sent to ' + state.selectedStudent.fullName + '.\n\nThey will receive an alert to accept the physical copy.');
                closePeerInterchangeModal();
                closeProfileModal();
                fetchViewerHoldings();
            } else {
                alert('Transfer Error: ' + (data.message || 'Unable to initiate transfer.'));
            }
        })
        .catch(err => {
            alert('Failed to send transfer request: ' + err.message);
        })
        .finally(() => {
            if (btn) {
                btn.disabled = false;
                btn.textContent = 'Send Handover Request';
            }
        });
    }

    function escapeHtml(text) {
        if (!text) return '';
        const map = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        };
        return text.toString().replace(/[&<>"']/g, m => map[m]);
    }

    // Expose methods to global scope
    window.studentDirectory = {
        changePage,
        viewProfile,
        quickHandover,
        closeProfileModal,
        closePeerInterchangeModal,
        submitPeerHandover
    };

    window.closeProfileModal = closeProfileModal;
    window.closePeerInterchangeModal = closePeerInterchangeModal;
    window.submitPeerHandover = submitPeerHandover;

})();
