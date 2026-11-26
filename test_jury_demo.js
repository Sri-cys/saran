const http = require('http');

function post(path, body, cookie = '') {
    return new Promise((resolve, reject) => {
        const data = JSON.stringify(body);
        const req = http.request({
            hostname: '127.0.0.1',
            port: 8080,
            path: path,
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(data),
                'Cookie': cookie
            }
        }, (res) => {
            let resBody = '';
            res.on('data', c => resBody += c);
            res.on('end', () => {
                const setCookie = res.headers['set-cookie'];
                let cookieHeader = '';
                if (setCookie) {
                    cookieHeader = Array.isArray(setCookie) ? setCookie.join('; ') : setCookie;
                }
                try {
                    resolve({ status: res.statusCode, data: JSON.parse(resBody), cookie: cookieHeader });
                } catch (e) {
                    resolve({ status: res.statusCode, raw: resBody, cookie: cookieHeader });
                }
            });
        });
        req.on('error', reject);
        req.write(data);
        req.end();
    });
}

function get(path, cookie = '') {
    return new Promise((resolve, reject) => {
        const req = http.request({
            hostname: '127.0.0.1',
            port: 8080,
            path: path,
            method: 'GET',
            headers: {
                'Cookie': cookie
            }
        }, (res) => {
            let resBody = '';
            res.on('data', c => resBody += c);
            res.on('end', () => {
                try {
                    resolve({ status: res.statusCode, data: JSON.parse(resBody), headers: res.headers });
                } catch (e) {
                    resolve({ status: res.statusCode, raw: resBody, headers: res.headers });
                }
            });
        });
        req.on('error', reject);
        req.end();
    });
}

function assert(condition, message) {
    if (!condition) {
        console.error('FAIL: ' + message);
        process.exit(1);
    }
    console.log('PASS: ' + message);
}

async function runTests() {
    console.log('--- Starting PSG iTech Library Jury Demo Tests ---');

    // 1. Check Gateway & Mode
    const gw = await get('/payments/razorpay');
    assert(gw.status === 200, 'GET /payments/razorpay responds 200');
    assert(gw.data.paymentMode === 'DEMO', `Payment mode is DEMO (got ${gw.data.paymentMode})`);
    assert(gw.data.mockGateway === true, 'Mock gateway indicator is true');

    // 2. Login as Student Sivakathir S (userId: 4)
    const loginSiva = await post('/login', { identifier: 'sivakathir.22cs@psgitech.ac.in', password: 'Student@123' });
    if (!loginSiva.data || !loginSiva.data.success) {
        console.error('Login Sivakathir failed:', loginSiva);
    }
    assert(loginSiva.status === 200 && loginSiva.data.success, 'Student Sivakathir S logged in successfully');
    const sivaCookie = loginSiva.cookie;

    // 3. Fetch fines for Sivakathir S
    const finesSiva = await get('/fines/my', sivaCookie);
    assert(finesSiva.status === 200 && finesSiva.data.success, 'Fetched fines for Sivakathir S');
    const sivaFines = finesSiva.data.fines.filter(f => f.status === 'UNPAID');
    assert(sivaFines.length >= 3, `Sivakathir S has 3 outstanding demo fines (found ${sivaFines.length})`);
    assert(finesSiva.data.summary.borrowingBlocked === true, 'Sivakathir S borrowing is initially blocked due to outstanding fines');
    console.log(`Sivakathir S initial outstanding: ₹${finesSiva.data.summary.totalOutstanding}`);

    // 4. Pay Fine #1 via Demo Mode
    const payFine1 = await post('/payments/razorpay/create-order', { action: 'create_order', type: 'INDIVIDUAL', fineId: 1 }, sivaCookie);
    assert(payFine1.status === 200 && payFine1.data.success, 'Pay Fine #1 succeeded');
    assert(payFine1.data.demoMode === true, 'Response confirms demoMode = true');
    assert(payFine1.data.status === 'SUCCESS', 'Payment status is SUCCESS');
    assert(payFine1.data.receiptNumber && payFine1.data.receiptNumber.startsWith('RCPT-DEMO-'), `Receipt generated: ${payFine1.data.receiptNumber}`);
    assert(payFine1.data.referenceId && payFine1.data.referenceId.startsWith('DEMO_TXN_'), `Reference ID generated: ${payFine1.data.referenceId}`);
    const sivaPayId = payFine1.data.paymentId;

    // 5. Verify Receipt for Fine #1
    const rcpt = await get(`/receipt?paymentId=${sivaPayId}`, sivaCookie);
    assert(rcpt.status === 200 && rcpt.data.success, 'Official receipt retrieved successfully');
    assert(rcpt.data.receipt.receiptNumber === payFine1.data.receiptNumber, 'Receipt number matches');

    // 6. Verify PDF download endpoint responds 200
    const pdf = await get(`/receipt/pdf?paymentId=${sivaPayId}`, sivaCookie);
    assert(pdf.status === 200 && pdf.headers['content-type'] === 'application/pdf', 'Receipt PDF downloaded successfully');

    // 7. Verify updated balance for Sivakathir S
    const finesSivaAfter1 = await get('/fines/my', sivaCookie);
    assert(finesSivaAfter1.data.summary.totalPaid === '28.00', `Total paid is ₹28.00 (got ${finesSivaAfter1.data.summary.totalPaid})`);
    console.log(`Sivakathir S remaining outstanding after paying fine #1: ₹${finesSivaAfter1.data.summary.totalOutstanding}`);

    // 8. Bulk Pay All remaining fines for Sivakathir S
    const payBulk = await post('/payments/razorpay/create-order', { action: 'create_order', type: 'BULK' }, sivaCookie);
    assert(payBulk.status === 200 && payBulk.data.success, 'Bulk Pay All succeeded for Sivakathir S');
    assert(payBulk.data.demoMode === true, 'Bulk response confirms demoMode = true');
    assert(payBulk.data.status === 'SUCCESS', 'Bulk status is SUCCESS');

    // 9. Verify borrowing unblocked for Sivakathir S
    const finesSivaZero = await get('/fines/my', sivaCookie);
    assert(parseFloat(finesSivaZero.data.summary.totalOutstanding) === 0, 'Total outstanding is now ₹0.00');
    assert(finesSivaZero.data.summary.borrowingBlocked === false, 'Borrowing block lifted! Student account in good standing');

    // 10. Login as Student Saran Karthi (userId: 7)
    const loginSaran = await post('/login', { identifier: '25z304@psgitech.ac.in', password: 'Saran@2111' });
    assert(loginSaran.status === 200 && loginSaran.data.success, 'Student Saran Karthi logged in successfully');
    const saranCookie = loginSaran.cookie;

    // 11. Verify Student Isolation: Saran sees only Saran's fines
    const finesSaran = await get('/fines/my', saranCookie);
    assert(finesSaran.status === 200 && finesSaran.data.success, 'Fetched fines for Saran Karthi');
    const saranFines = finesSaran.data.fines.filter(f => f.status === 'UNPAID');
    assert(saranFines.length >= 3, `Saran Karthi has 3 outstanding demo fines (found ${saranFines.length})`);
    assert(finesSaran.data.summary.borrowingBlocked === true, 'Saran Karthi borrowing is blocked due to dues');
    console.log(`Saran Karthi initial outstanding: ₹${finesSaran.data.summary.totalOutstanding}`);

    // 12. Security Test: Saran cannot pay Sivakathir's fine (#1) or view Sivakathir's receipt
    const crossPay = await post('/payments/razorpay/create-order', { action: 'create_order', type: 'INDIVIDUAL', fineId: 1 }, saranCookie);
    assert(crossPay.status === 403 || crossPay.status === 400, `Cross-student payment correctly rejected (HTTP ${crossPay.status})`);

    const crossReceipt = await get(`/receipt?paymentId=${sivaPayId}`, saranCookie);
    assert(crossReceipt.status === 403, `Cross-student receipt access rejected (HTTP ${crossReceipt.status})`);

    // 13. Pay Fine #3 for Saran Karthi via Demo Mode
    const payFine3 = await post('/payments/razorpay/create-order', { action: 'create_order', type: 'INDIVIDUAL', fineId: 3 }, saranCookie);
    assert(payFine3.status === 200 && payFine3.data.success, 'Pay Fine #3 succeeded for Saran Karthi');
    assert(payFine3.data.demoMode === true, 'Saran demoMode = true');

    const finesSaranAfter = await get('/fines/my', saranCookie);
    assert(finesSaranAfter.data.summary.totalPaid === '54.00', `Saran total paid is ₹54.00 (got ${finesSaranAfter.data.summary.totalPaid})`);
    console.log(`Saran Karthi remaining outstanding: ₹${finesSaranAfter.data.summary.totalOutstanding}`);

    console.log('--- ALL JURY DEMO PAYMENT TESTS PASSED (13/13) ---');
}

runTests().catch(err => {
    console.error('Test error:', err);
    process.exit(1);
});
