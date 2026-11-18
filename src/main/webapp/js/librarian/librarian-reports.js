/**
 * PSG iTech Central Library - Librarian Reports & Activity Logs Module
 * Orchestrates Activity Logs Audit Trail and all 9 Strategic Analytics Reports.
 */

import { CTX, apiFetch, showToast } from './librarian-api.js';

let currentReportData = null;

export function initReportsModule() {
    setupActivityLogs();
    setupReportsAnalytics();
}

// ============================================================================
// 1. ACTIVITY LOGS AUDIT TRAIL
// ============================================================================
export async function loadActivityLogs(page = 1) {
    const eventType = document.getElementById('logEventTypeFilter')?.value || '';
    const from = document.getElementById('logFromDate')?.value || '';
    const to = document.getElementById('logToDate')?.value || '';
    const tbody = document.getElementById('activityLogsTableBody');
    if (!tbody) return;

    tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px;">Loading audit logs...</td></tr>';

    try {
        let url = `/api/librarian/reports/activity-logs?page=${page}&limit=50`;
        if (eventType) url += `&eventType=${encodeURIComponent(eventType)}`;
        if (from) url += `&from=${encodeURIComponent(from)}`;
        if (to) url += `&to=${encodeURIComponent(to)}`;

        const res = await apiFetch(url);
        if (!res.success || !res.data || !res.data.items || res.data.items.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px; color: var(--navy-light);">No activity log entries found.</td></tr>';
            return;
        }

        tbody.innerHTML = res.data.items.map(l => `
            <tr>
                <td><strong>#${l.logId}</strong></td>
                <td><span class="status-pill status-pill-available" style="font-family: monospace; font-size: 11px;">${escapeHtml(l.eventType)}</span></td>
                <td>
                    <div><strong>${escapeHtml(l.actorName || 'Staff')}</strong></div>
                    <div style="font-size: 11px; color: var(--navy-light);">${escapeHtml(l.actorRole)} (ID #${l.actorId})</div>
                </td>
                <td>
                    <div><code>${escapeHtml(l.targetEntity)}</code></div>
                    <div style="font-size: 11px; color: var(--navy-light);">Target ID: ${escapeHtml(l.targetId)}</div>
                </td>
                <td style="font-size: 12px; max-width: 300px;">${escapeHtml(l.actionDetails || '')}</td>
                <td style="font-size: 11px; color: var(--navy-light);">${escapeHtml(l.ipAddress || 'Internal')}</td>
                <td style="font-size: 11px; color: var(--navy-light); white-space: nowrap;">${escapeHtml(l.createdAt || '')}</td>
            </tr>
        `).join('');

        renderPagination(res.data.page, res.data.totalPages);
    } catch (err) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: #E02020; padding: 24px;">Failed to load logs: ${escapeHtml(err.message)}</td></tr>`;
    }
}

function setupActivityLogs() {
    document.getElementById('logFilterBtn')?.addEventListener('click', () => loadActivityLogs(1));
    document.getElementById('logResetBtn')?.addEventListener('click', () => {
        const ev = document.getElementById('logEventTypeFilter');
        const fr = document.getElementById('logFromDate');
        const to = document.getElementById('logToDate');
        if (ev) ev.value = '';
        if (fr) fr.value = '';
        if (to) to.value = '';
        loadActivityLogs(1);
    });
}

function renderPagination(page, totalPages) {
    const pag = document.getElementById('logsPagination');
    if (!pag) return;
    if (totalPages <= 1) {
        pag.innerHTML = '';
        return;
    }

    pag.innerHTML = `
        <div style="display: flex; gap: 8px; align-items: center; justify-content: flex-end; padding: 12px 0;">
            <button class="btn-quick" ${page <= 1 ? 'disabled' : ''} onclick="window.librarianReports.loadActivityLogs(${page - 1})">Previous</button>
            <span style="font-size: 12px;">Page ${page} of ${totalPages}</span>
            <button class="btn-quick" ${page >= totalPages ? 'disabled' : ''} onclick="window.librarianReports.loadActivityLogs(${page + 1})">Next</button>
        </div>
    `;
}

// ============================================================================
// 2. REPORTS & ANALYTICS (9 STRATEGIC REPORTS)
// ============================================================================
function setupReportsAnalytics() {
    const presetSelect = document.getElementById('reportPresetSelect');
    const customDateGroup = document.getElementById('reportCustomDateGroup');

    presetSelect?.addEventListener('change', () => {
        if (presetSelect.value === 'CUSTOM') {
            if (customDateGroup) customDateGroup.style.display = 'flex';
        } else {
            if (customDateGroup) customDateGroup.style.display = 'none';
        }
    });

    document.getElementById('generateReportBtn')?.addEventListener('click', generateCurrentReport);
    document.getElementById('exportReportCsvBtn')?.addEventListener('click', exportCurrentReportCsv);
}

export async function generateCurrentReport() {
    const reportType = document.getElementById('reportTypeSelect')?.value || 'MONTHLY_BORROW_VS_RETURN';
    const preset = document.getElementById('reportPresetSelect')?.value || 'LAST_30_DAYS';
    const from = document.getElementById('reportFromDate')?.value || '';
    const to = document.getElementById('reportToDate')?.value || '';
    const dept = document.getElementById('reportDeptSelect')?.value || '';

    const contentArea = document.getElementById('reportContentArea');
    if (!contentArea) return;

    contentArea.innerHTML = '<div style="padding: 40px; text-align: center; color: var(--navy-primary);">Generating report and computing analytics...</div>';

    try {
        let url = `/api/librarian/reports/generate?reportType=${encodeURIComponent(reportType)}&preset=${encodeURIComponent(preset)}`;
        if (preset === 'CUSTOM') {
            url += `&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
        }
        if (dept && dept !== 'ALL') {
            url += `&department=${encodeURIComponent(dept)}`;
        }

        const res = await apiFetch(url);
        if (!res.success || !res.data) {
            contentArea.innerHTML = `<div style="padding: 24px; color: #E02020; text-align: center;">Error: ${escapeHtml(res.error?.message || 'Report generation failed')}</div>`;
            return;
        }

        currentReportData = res.data;
        renderReportView(res.data);
    } catch (err) {
        contentArea.innerHTML = `<div style="padding: 24px; color: #E02020; text-align: center;">Error generating report: ${escapeHtml(err.message)}</div>`;
    }
}

function renderReportView(data) {
    const contentArea = document.getElementById('reportContentArea');
    if (!contentArea) return;

    // Header & Summary Strip
    let summaryHtml = '';
    if (data.summary && Object.keys(data.summary).length > 0) {
        summaryHtml = `
            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 24px;">
                ${Object.entries(data.summary).map(([k, v]) => `
                    <div style="background: white; border: 1px solid var(--border-color); border-radius: 8px; padding: 14px 18px; box-shadow: 0 1px 3px rgba(0,0,0,0.04);">
                        <div style="font-size: 11px; text-transform: uppercase; color: var(--navy-light); font-weight: 600; margin-bottom: 4px;">${formatCamelCase(k)}</div>
                        <div style="font-size: 20px; font-weight: bold; color: var(--navy-primary);">${v}</div>
                    </div>
                `).join('')}
            </div>
        `;
    }

    // Dynamic Chart Card
    let chartHtml = `
        <div style="background: white; border: 1px solid var(--border-color); border-radius: 8px; padding: 20px; margin-bottom: 24px; box-shadow: 0 1px 4px rgba(0,0,0,0.04);">
            <h3 style="font-size: 16px; font-weight: bold; color: var(--navy-primary); margin-bottom: 16px;">Visual Analytics: ${escapeHtml(data.title)}</h3>
            <canvas id="reportChartCanvas" height="120" style="width: 100%; max-height: 280px;"></canvas>
        </div>
    `;

    // Data Table Card
    let tableHtml = `
        <div style="background: white; border: 1px solid var(--border-color); border-radius: 8px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.04);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px;">
                <h3 style="font-size: 16px; font-weight: bold; color: var(--navy-primary);">Tabular Report (${data.rows.length} records)</h3>
                <span style="font-size: 12px; color: var(--navy-light);">Period: ${data.fromDate} to ${data.toDate}</span>
            </div>
            <div class="data-table-container">
                <table class="data-table">
                    <thead>
                        <tr>
                            ${data.headers.map(h => `<th>${escapeHtml(h)}</th>`).join('')}
                        </tr>
                    </thead>
                    <tbody>
                        ${data.rows.length > 0 ? data.rows.map(row => `
                            <tr>
                                ${row.map(cell => `<td>${escapeHtml(String(cell != null ? cell : ''))}</td>`).join('')}
                            </tr>
                        `).join('') : '<tr><td colspan="' + data.headers.length + '" style="text-align: center; padding: 20px;">No data in selected date range.</td></tr>'}
                    </tbody>
                </table>
            </div>
        </div>
    `;

    contentArea.innerHTML = summaryHtml + chartHtml + tableHtml;

    // Draw Chart onto Canvas
    drawReportChart(data.chartData);
}

function drawReportChart(chartData) {
    const canvas = document.getElementById('reportChartCanvas');
    if (!canvas || !chartData || !chartData.labels || chartData.labels.length === 0) return;

    const ctx = canvas.getContext('2d');
    const width = canvas.width = canvas.parentElement.clientWidth - 40;
    const height = canvas.height = 240;

    ctx.clearRect(0, 0, width, height);

    const labels = chartData.labels;
    const datasets = chartData.datasets || [];
    if (datasets.length === 0) return;

    // Find max value
    let maxVal = 1;
    datasets.forEach(ds => {
        (ds.data || []).forEach(v => {
            const num = parseFloat(v) || 0;
            if (num > maxVal) maxVal = num;
        });
    });

    const paddingLeft = 45;
    const paddingBottom = 40;
    const paddingTop = 20;
    const paddingRight = 20;

    const chartW = width - paddingLeft - paddingRight;
    const chartH = height - paddingTop - paddingBottom;

    // Axes
    ctx.strokeStyle = '#E2E8F0';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(paddingLeft, paddingTop);
    ctx.lineTo(paddingLeft, height - paddingBottom);
    ctx.lineTo(width - paddingRight, height - paddingBottom);
    ctx.stroke();

    // Bars
    const groupCount = labels.length;
    const groupWidth = chartW / groupCount;
    const barWidth = Math.max(8, (groupWidth * 0.7) / datasets.length);

    labels.forEach((lbl, gIdx) => {
        const groupX = paddingLeft + (gIdx * groupWidth);

        datasets.forEach((ds, dIdx) => {
            const val = parseFloat(ds.data[gIdx]) || 0;
            const barH = (val / maxVal) * chartH;
            const barX = groupX + (dIdx * barWidth) + (groupWidth * 0.15);
            const barY = height - paddingBottom - barH;

            ctx.fillStyle = ds.color || '#0A2540';
            ctx.fillRect(barX, barY, barWidth - 2, barH);
        });

        // Label
        ctx.fillStyle = '#64748B';
        ctx.font = '10px Inter, sans-serif';
        ctx.textAlign = 'center';
        const displayLabel = lbl.length > 9 ? lbl.substring(0, 8) + '..' : lbl;
        ctx.fillText(displayLabel, groupX + (groupWidth / 2), height - paddingBottom + 16);
    });

    // Legend
    let legX = paddingLeft;
    datasets.forEach(ds => {
        ctx.fillStyle = ds.color || '#0A2540';
        ctx.fillRect(legX, 6, 12, 10);
        ctx.fillStyle = '#1E293B';
        ctx.font = '11px Inter, sans-serif';
        ctx.textAlign = 'left';
        ctx.fillText(ds.label || 'Series', legX + 16, 15);
        legX += 110;
    });
}

function exportCurrentReportCsv() {
    const reportType = document.getElementById('reportTypeSelect')?.value || 'MONTHLY_BORROW_VS_RETURN';
    const preset = document.getElementById('reportPresetSelect')?.value || 'LAST_30_DAYS';
    const from = document.getElementById('reportFromDate')?.value || '';
    const to = document.getElementById('reportToDate')?.value || '';
    const dept = document.getElementById('reportDeptSelect')?.value || '';

    let url = `/api/librarian/reports/export?reportType=${encodeURIComponent(reportType)}&preset=${encodeURIComponent(preset)}`;
    if (preset === 'CUSTOM') {
        url += `&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
    }
    if (dept && dept !== 'ALL') {
        url += `&department=${encodeURIComponent(dept)}`;
    }

    // Trigger file download
    window.location.href = url;
    showToast('Exporting report as formula-safe CSV...', 'info');
}

window.librarianReports = {
    loadActivityLogs,
    generateCurrentReport,
    exportCurrentReportCsv
};

function formatCamelCase(str) {
    if (!str) return '';
    return str.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase());
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
