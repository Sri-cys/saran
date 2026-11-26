const http = require('http');
const crypto = require('crypto');

const RAZORPAY_KEY_SECRET = '2bekkNvTLar3OE2YEEjjP2xn';

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
    console.log('=== PSG iTech Library - Razorpay Test Mode & Demo Data Verification ===');

    // 1. Verify Gateway configuration
    const gw = await get('/payments/razorpay');
    assert(gw.status === 200, 'GET /payments/razorpay responds 200');
    assert(gw.data.paymentMode === 'RAZORPAY', `Payment mode is RAZORPAY (got ${gw.data.paymentMode})`);
    assert(gw.data.keyId === 'rzp_test_U1hOr0s9TYhGRb', 'Razorpay Test Key ID verified');

    // 2. Login as Sivakathir S (User ID: 4)
    const loginSiva = await post('/login', { identifier: 'sivakathir.22cs@psgitech.ac.in', password: 'Student@123' });
    assert(loginSiva.status === 200 && loginSiva.data.success, 'Student Sivakathir S logged in successfully');
    const sivaCookie = loginSiva.cookie;

    // 3. Verify exactly 3 outstanding fines for Sivakathir S
    const finesSiva = await get('/fines/my', sivaCookie);
    assert(finesSiva.status === 200 && finesSiva.data.success, 'Retrieved fines for Sivakathir S');
    const sivaOpenFines = finesSiva.data.fines.filter(f => f.status === 'UNPAID');
    assert(sivaOpenFines.length === 3, `Sivakathir S has exactly 3 outstanding fines (found ${sivaOpenFines.length})`);

    // Verify institutional calculation (₹2.00/day, 0 grace days) for Sivakathir S
    let sivaTotalExpected = 0;
    for (const f of sivaOpenFines) {
        const expected = f.overdueDays * 2.0;
        assert(parseFloat(f.outstandingAmount) === expected, `Fine #${f.fineId}: ${f.overdueDays} days @ ₹2/day = ₹${expected.toFixed(2)} (got ₹${f.outstandingAmount})`);
        sivaTotalExpected += expected;
    }
    assert(parseFloat(finesSiva.data.summary.totalOutstanding) === sivaTotalExpected, `Total outstanding balance is ₹${sivaTotalExpected.toFixed(2)}`);
    assert(finesSiva.data.summary.borrowingBlocked === true, 'Borrowing status is correctly Blocked due to unpaid fines');
    console.log(`Sivakathir S verified with 3 fines totaling ₹${sivaTotalExpected.toFixed(2)}`);

    // 4. Login as Saran Karthi (User ID: 7)
    const loginSaran = await post('/login', { identifier: '25z304@psgitech.ac.in', password: 'Saran@2111' });
    assert(loginSaran.status === 200 && loginSaran.data.success, 'Student Saran Karthi logged in successfully');
    const saranCookie = loginSaran.cookie;

    // 5. Verify exactly 3 outstanding fines for Saran Karthi
    const finesSaran = await get('/fines/my', saranCookie);
    assert(finesSaran.status === 200 && finesSaran.data.success, 'Retrieved fines for Saran Karthi');
    const saranOpenFines = finesSaran.data.fines.filter(f => f.status === 'UNPAID');
    assert(saranOpenFines.length === 3, `Saran Karthi has exactly 3 outstanding fines (found ${saranOpenFines.length})`);

    // Verify institutional calculation (₹2.00/day, 0 grace days) for Saran Karthi
    let saranTotalExpected = 0;
    for (const f of saranOpenFines) {
        const expected = f.overdueDays * 2.0;
        assert(parseFloat(f.outstandingAmount) === expected, `Fine #${f.fineId}: ${f.overdueDays} days @ ₹2/day = ₹${expected.toFixed(2)} (got ₹${f.outstandingAmount})`);
        saranTotalExpected += expected;
    }
    assert(parseFloat(finesSaran.data.summary.totalOutstanding) === saranTotalExpected, `Total outstanding balance is ₹${saranTotalExpected.toFixed(2)}`);
    assert(finesSaran.data.summary.borrowingBlocked === true, 'Borrowing status is correctly Blocked due to unpaid fines');
    console.log(`Saran Karthi verified with 3 fines totaling ₹${saranTotalExpected.toFixed(2)}`);

    // 6. Account Isolation Test
    const crossFineCheck = finesSaran.data.fines.some(f => f.studentId === 4);
    assert(!crossFineCheck, 'Student Isolation: Saran cannot see any of Sivakathir\'s fines');
    const crossPay = await post('/payments/razorpay/create-order', { action: 'create_order', type: 'INDIVIDUAL', fineId: 1 }, saranCookie);
    assert(crossPay.status === 403, `Student Isolation: Saran cannot initiate payment on Sivakathir's fine (HTTP ${crossPay.status})`);

    // 7. Test Razorpay Order Creation via Cloud Orders API
    const createOrderRes = await post('/payments/razorpay/create-order', { action: 'create_order', type: 'INDIVIDUAL', fineId: 1 }, sivaCookie);
    assert(createOrderRes.status === 200 && createOrderRes.data.success, 'Razorpay order created successfully');
    assert(createOrderRes.data.orderId && createOrderRes.data.orderId.startsWith('order_'), `Valid Razorpay Order ID returned: ${createOrderRes.data.orderId}`);
    assert(createOrderRes.data.demoMode === undefined, 'Simulated Demo bypass is NOT triggered (real Razorpay checkout order returned)');
    const orderId = createOrderRes.data.orderId;
    const paymentId = createOrderRes.data.paymentId;

    // 8. Test Mock Bank "Failure" Flow: Leaves fine outstanding
    const failRes = await post('/payments/razorpay/failed', { orderId: orderId, paymentId: paymentId, reason: 'Payment cancelled by user at bank page' }, sivaCookie);
    assert(failRes.status === 200, 'Payment failure recorded');
    const finesAfterFail = await get('/fines/my', sivaCookie);
    const fine1AfterFail = finesAfterFail.data.fines.find(f => f.fineId === 1);
    assert(fine1AfterFail && fine1AfterFail.status === 'UNPAID', 'Fine #1 remains UNPAID after bank failure selection');
    assert(parseFloat(finesAfterFail.data.summary.totalOutstanding) === sivaTotalExpected, 'Total outstanding balance remains unchanged');

    // 9. Test Mock Bank "Success" Flow: Updates fine, records payment, issues receipt
    const createOrderRes2 = await post('/payments/razorpay/create-order', { action: 'create_order', type: 'INDIVIDUAL', fineId: 1 }, sivaCookie);
    const orderId2 = createOrderRes2.data.orderId;
    const paymentId2 = createOrderRes2.data.paymentId;
    const rzpPayId = `pay_test_${Date.now()}`;
    const validSignature = crypto.createHmac('sha256', RAZORPAY_KEY_SECRET).update(`${orderId2}|${rzpPayId}`).digest('hex');

    const verifyRes = await post('/payments/razorpay/verify', {
        razorpay_order_id: orderId2,
        razorpay_payment_id: rzpPayId,
        razorpay_signature: validSignature
    }, sivaCookie);
    assert(verifyRes.status === 200 && verifyRes.data.success, 'Razorpay HMAC-SHA256 signature verified by backend');
    assert(verifyRes.data.receiptNumber && verifyRes.data.receiptNumber.startsWith('RCPT-'), `Official receipt issued: ${verifyRes.data.receiptNumber}`);

    // Verify fine updated to PAID and balance decremented
    const finesAfterSuccess = await get('/fines/my', sivaCookie);
    const fine1AfterSuccess = finesAfterSuccess.data.fines.find(f => f.fineId === 1);
    assert(fine1AfterSuccess && fine1AfterSuccess.status === 'PAID', 'Fine #1 is now marked PAID');
    assert(parseFloat(finesAfterSuccess.data.summary.totalOutstanding) === (sivaTotalExpected - 28.0), `Remaining balance accurately decremented to ₹${(sivaTotalExpected - 28.0).toFixed(2)}`);

    // Verify downloadable receipt
    const rcptRes = await get(`/receipt?paymentId=${paymentId2}`, sivaCookie);
    assert(rcptRes.status === 200 && rcptRes.data.success, 'Official receipt retrieved from database');
    assert(rcptRes.data.receipt.receiptNumber === verifyRes.data.receiptNumber, 'Receipt number matches verified transaction');

    const pdfRes = await get(`/receipt/pdf?paymentId=${paymentId2}`, sivaCookie);
    assert(pdfRes.status === 200 && pdfRes.headers['content-type'] === 'application/pdf', 'Downloadable institutional PDF receipt generated');

    console.log('=== ALL RAZORPAY TEST MODE & DEMO DATA VERIFICATIONS PASSED ===');
}

runTests().catch(err => {
    console.error('Test error:', err);
    process.exit(1);
});
