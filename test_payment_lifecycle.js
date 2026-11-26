const http = require('http');
const https = require('https');
const crypto = require('crypto');

const RAZORPAY_KEY_ID = 'rzp_test_U1hOr0s9TYhGRb';
const RAZORPAY_KEY_SECRET = '2bekkNvTLar3OE2YEEjjP2xn';

function httpRequest(options, postData = null) {
    return new Promise((resolve, reject) => {
        const req = http.request(options, (res) => {
            let data = [];
            res.on('data', chunk => data.push(chunk));
            res.on('end', () => {
                const buffer = Buffer.concat(data);
                resolve({
                    statusCode: res.statusCode,
                    headers: res.headers,
                    body: buffer.toString('utf-8'),
                    buffer: buffer
                });
            });
        });
        req.on('error', reject);
        if (postData) {
            req.write(typeof postData === 'string' ? postData : JSON.stringify(postData));
        }
        req.end();
    });
}

function fetchRazorpayCloudOrder(orderId) {
    return new Promise((resolve, reject) => {
        const auth = Buffer.from(`${RAZORPAY_KEY_ID}:${RAZORPAY_KEY_SECRET}`).toString('base64');
        const req = https.request(`https://api.razorpay.com/v1/orders/${orderId}`, {
            method: 'GET',
            headers: { 'Authorization': `Basic ${auth}` }
        }, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                resolve({
                    statusCode: res.statusCode,
                    body: JSON.parse(data)
                });
            });
        });
        req.on('error', reject);
        req.end();
    });
}

async function runLifecycleTests() {
    console.log('=== STARTING PAYMENT LIFECYCLE & UPI WORKFLOW TESTS ===\n');
    let passed = 0;
    let failed = 0;

    function assert(cond, name, details = '') {
        if (cond) {
            console.log(`[PASS] ${name}`);
            passed++;
        } else {
            console.error(`[FAIL] ${name} ${details}`);
            failed++;
        }
    }

    try {
        // 1. Authenticate as Student 7
        console.log('--- Test 1: Student Login & Profile Retrieval ---');
        const loginRes = await httpRequest({
            hostname: 'localhost',
            port: 8080,
            path: '/login',
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, {
            email: '25z304@psgitech.ac.in',
            password: 'Password@123'
        });
        const cookie = loginRes.headers['set-cookie'] ? loginRes.headers['set-cookie'][0].split(';')[0] : '';
        assert(loginRes.statusCode === 200 && cookie, 'Logged in successfully as Student 7');

        const finesRes = await httpRequest({
            hostname: 'localhost',
            port: 8080,
            path: '/my-fines',
            method: 'GET',
            headers: { 'Cookie': cookie }
        });
        const finesData = JSON.parse(finesRes.body);
        assert(finesData.success === true, 'Fines loaded');
        assert(finesData.summary.phone && finesData.summary.email,
            `Profile contains contact for UPI prefill: phone=${finesData.summary.phone}, email=${finesData.summary.email}`);

        // 2. Test Manual UPI QR Initiation
        console.log('\n--- Test 2: Manual UPI QR Initiation ---');
        const manualUpiRes = await httpRequest({
            hostname: 'localhost',
            port: 8080,
            path: '/payments',
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Cookie': cookie }
        }, {
            action: 'initiate',
            fineId: 3,
            amount: 34.00
        });
        assert(manualUpiRes.statusCode === 200, 'Manual UPI initiation returned HTTP 200');
        const upiData = JSON.parse(manualUpiRes.body);
        assert(upiData.success === true && upiData.instructions && upiData.instructions.upiUri.includes('library@psgitech.ac.in'),
            'UPI URI and payee details generated for manual QR/UTR workflow');

        // 3. Test Switching from Manual UPI to Razorpay (Auto-cancel prior uncompleted attempt)
        console.log('\n--- Test 3: Lifecycle Transition (Manual UPI -> Razorpay Checkout) ---');
        const rzpOrderRes1 = await httpRequest({
            hostname: 'localhost',
            port: 8080,
            path: '/payments/razorpay/create-order',
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Cookie': cookie }
        }, {
            action: 'create_order',
            type: 'INDIVIDUAL',
            fineId: 3
        });
        assert(rzpOrderRes1.statusCode === 200,
            'Razorpay order creation succeeded without "active payment in progress" lock');
        const order1 = JSON.parse(rzpOrderRes1.body);
        assert(order1.success === true && order1.orderId.startsWith('order_'),
            `Cloud order generated: ${order1.orderId}`);

        // 4. Test User Dismissing Checkout (Modal Cancel)
        console.log('\n--- Test 4: Modal Dismissal / Cancellation ---');
        const cancelRes = await httpRequest({
            hostname: 'localhost',
            port: 8080,
            path: '/payments/razorpay/cancel',
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Cookie': cookie }
        }, {
            action: 'cancel',
            orderId: order1.orderId,
            reason: 'Student closed window'
        });
        assert(cancelRes.statusCode === 200, 'Payment cancellation acknowledged with HTTP 200');

        // 5. Test Immediate Retry after Cancellation (Must succeed without any blocking)
        console.log('\n--- Test 5: Immediate Retry After Cancellation ---');
        const rzpOrderRes2 = await httpRequest({
            hostname: 'localhost',
            port: 8080,
            path: '/payments/razorpay/create-order',
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Cookie': cookie }
        }, {
            action: 'create_order',
            type: 'INDIVIDUAL',
            fineId: 3
        });
        assert(rzpOrderRes2.statusCode === 200,
            'Retry order creation succeeded smoothly after cancellation');
        const order2 = JSON.parse(rzpOrderRes2.body);

        // 6. Confirm Order Exists in Razorpay Cloud
        console.log('\n--- Test 6: Verify in Razorpay Cloud ---');
        const cloudCheck = await fetchRazorpayCloudOrder(order2.orderId);
        assert(cloudCheck.statusCode === 200 && cloudCheck.body.status === 'created',
            `Razorpay cloud confirmed order #${order2.orderId} is active with amount ${cloudCheck.body.amount} paise`);

        // 7. Verify Successful Payment Settlement
        console.log('\n--- Test 7: Verify Payment Settlement & Signature ---');
        const paymentId = `pay_final_test_${Date.now()}`;
        const signature = crypto.createHmac('sha256', RAZORPAY_KEY_SECRET)
            .update(`${order2.orderId}|${paymentId}`)
            .digest('hex');

        const verifyRes = await httpRequest({
            hostname: 'localhost',
            port: 8080,
            path: '/payments/razorpay/verify',
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Cookie': cookie }
        }, {
            action: 'verify',
            razorpay_order_id: order2.orderId,
            razorpay_payment_id: paymentId,
            razorpay_signature: signature
        });
        assert(verifyRes.statusCode === 200, 'Payment verification returned HTTP 200');
        const verifyData = JSON.parse(verifyRes.body);
        assert(verifyData.success === true && verifyData.receiptNumber,
            `Receipt generated: ${verifyData.receiptNumber}`);

        // 8. Confirm Fine is Fully Settled
        console.log('\n--- Test 8: Verify Balance in Database ---');
        const checkFinesRes = await httpRequest({
            hostname: 'localhost',
            port: 8080,
            path: '/my-fines',
            method: 'GET',
            headers: { 'Cookie': cookie }
        });
        const finalFines = JSON.parse(checkFinesRes.body);
        const f3 = finalFines.fines.find(f => f.fineId === 3);
        assert(f3 && f3.status === 'PAID' && parseFloat(f3.outstandingAmount) === 0.00,
            `Fine #3 status is PAID, balance = ₹${f3 ? f3.outstandingAmount : 'N/A'}`);

        console.log(`\n=== LIFECYCLE TESTS SUMMARY: ${passed} PASSED, ${failed} FAILED ===`);
        process.exit(failed > 0 ? 1 : 0);
    } catch (err) {
        console.error('Lifecycle test execution error:', err);
        process.exit(1);
    }
}

runLifecycleTests();
