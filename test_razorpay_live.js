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
            headers: {
                'Authorization': `Basic ${auth}`
            }
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

async function runRazorpayIntegrationTest() {
    console.log('=== STARTING LIVE RAZORPAY PAYMENT WORKFLOW VERIFICATION ===\n');
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
        // 1. Authenticate as Student 7 (Saran Karthi)
        console.log('--- Step 1: Student Authentication ---');
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
        assert(loginRes.statusCode === 200 && cookie, 'Logged in as Student 7 (Saran Karthi)');

        // 2. Fetch outstanding fine
        console.log('\n--- Step 2: Fetch Outstanding Fines ---');
        const finesRes = await httpRequest({
            hostname: 'localhost',
            port: 8080,
            path: '/my-fines',
            method: 'GET',
            headers: { 'Cookie': cookie }
        });
        const finesData = JSON.parse(finesRes.body);
        assert(finesData.success === true, 'Fines loaded successfully');
        const fine = finesData.fines.find(f => f.fineId === 3);
        assert(fine && fine.status === 'UNPAID' && parseFloat(fine.outstandingAmount) === 34.00,
            `Fine #3 is UNPAID with outstanding amount: ₹${fine ? fine.outstandingAmount : 'N/A'}`);

        // 3. Security Check: Cannot pay another student's fine (Fine #1 belongs to student 4)
        console.log('\n--- Step 3: Authorization Check (Cross-account payment prevention) ---');
        const crossPayRes = await httpRequest({
            hostname: 'localhost',
            port: 8080,
            path: '/payments/razorpay/create-order',
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Cookie': cookie }
        }, {
            action: 'create_order',
            type: 'INDIVIDUAL',
            fineId: 1
        });
        assert(crossPayRes.statusCode === 403, 'Cross-student fine payment rejected with HTTP 403 Forbidden');

        // 4. Create authentic Razorpay cloud order for Fine #3
        console.log('\n--- Step 4: Create Live Razorpay Order ---');
        const orderRes = await httpRequest({
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
        assert(orderRes.statusCode === 200, 'Order creation endpoint returned HTTP 200');
        const orderData = JSON.parse(orderRes.body);
        assert(orderData.success === true && orderData.orderId && orderData.orderId.startsWith('order_'),
            `Razorpay Order created: orderId=${orderData.orderId}`);
        assert(orderData.amountPaise === 3400 && orderData.currency === 'INR',
            'Order correctly formatted: amountPaise=3400, currency=INR');

        // 5. Query Razorpay API directly to prove order exists in Razorpay's cloud!
        console.log('\n--- Step 5: Verify Order Exists in Razorpay Cloud ---');
        const cloudOrder = await fetchRazorpayCloudOrder(orderData.orderId);
        assert(cloudOrder.statusCode === 200,
            `Razorpay cloud confirmed order existence: HTTP ${cloudOrder.statusCode}`);
        assert(cloudOrder.body.id === orderData.orderId && cloudOrder.body.amount === 3400,
            `Razorpay cloud order details match: id=${cloudOrder.body.id}, amount=${cloudOrder.body.amount}, status=${cloudOrder.body.status}`);

        // 6. Signature Tampering Test (Invalid signature must be rejected)
        console.log('\n--- Step 6: Cryptographic Signature Tampering Check ---');
        const testPaymentId = `pay_live_test_${Date.now()}`;
        const badVerifyRes = await httpRequest({
            hostname: 'localhost',
            port: 8080,
            path: '/payments/razorpay/verify',
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Cookie': cookie }
        }, {
            action: 'verify',
            razorpay_order_id: orderData.orderId,
            razorpay_payment_id: testPaymentId,
            razorpay_signature: 'invalid_tampered_signature_hex_12345'
        });
        assert(badVerifyRes.statusCode === 400,
            'Tampered / Invalid signature rejected with HTTP 400 Bad Request');

        // 7. Verify with valid HMAC-SHA256 signature
        console.log('\n--- Step 7: Complete Valid Payment Verification ---');
        const validSignature = crypto.createHmac('sha256', RAZORPAY_KEY_SECRET)
            .update(`${orderData.orderId}|${testPaymentId}`)
            .digest('hex');
        
        const goodVerifyRes = await httpRequest({
            hostname: 'localhost',
            port: 8080,
            path: '/payments/razorpay/verify',
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Cookie': cookie }
        }, {
            action: 'verify',
            razorpay_order_id: orderData.orderId,
            razorpay_payment_id: testPaymentId,
            razorpay_signature: validSignature
        });
        assert(goodVerifyRes.statusCode === 200, 'Valid payment verification returned HTTP 200');
        const verifyData = JSON.parse(goodVerifyRes.body);
        assert(verifyData.success === true && verifyData.receiptNumber && verifyData.paymentId,
            `Payment successfully settled! Receipt: ${verifyData.receiptNumber}, Payment ID: ${verifyData.paymentId}`);

        // 8. Re-check fine balance after payment
        console.log('\n--- Step 8: Confirm Database Fine Status & Balance Update ---');
        const postFinesRes = await httpRequest({
            hostname: 'localhost',
            port: 8080,
            path: '/my-fines',
            method: 'GET',
            headers: { 'Cookie': cookie }
        });
        const postFinesData = JSON.parse(postFinesRes.body);
        const settledFine = postFinesData.fines.find(f => f.fineId === 3);
        assert(settledFine && settledFine.status === 'PAID' && parseFloat(settledFine.outstandingAmount) === 0.00,
            `Fine #3 is now PAID with outstandingAmount = ₹${settledFine ? settledFine.outstandingAmount : 'N/A'}`);
        
        const paymentRecord = postFinesData.payments.find(p => p.orderId === orderData.orderId || p.paymentId === verifyData.paymentId);
        assert(paymentRecord && paymentRecord.status === 'SUCCESS' && paymentRecord.method === 'RAZORPAY',
            `Payment history displays verified Razorpay transaction: Method=${paymentRecord ? paymentRecord.method : 'N/A'}, Status=${paymentRecord ? paymentRecord.status : 'N/A'}`);

        // 9. Download Official PDF Receipt
        console.log('\n--- Step 9: Official PDF Receipt Download ---');
        const pdfRes = await httpRequest({
            hostname: 'localhost',
            port: 8080,
            path: `/payments/receipt/pdf?paymentId=${verifyData.paymentId}`,
            method: 'GET',
            headers: { 'Cookie': cookie }
        });
        assert(pdfRes.statusCode === 200, 'PDF Receipt endpoint returned HTTP 200');
        assert(pdfRes.headers['content-type'] === 'application/pdf', 'Content-Type is application/pdf');
        assert(pdfRes.buffer.slice(0, 4).toString() === '%PDF', 'Binary stream starts with %PDF magic header');

        // 10. Duplicate Callback Idempotency Check
        console.log('\n--- Step 10: Duplicate Callback Idempotency Check ---');
        const dupVerifyRes = await httpRequest({
            hostname: 'localhost',
            port: 8080,
            path: '/payments/razorpay/verify',
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Cookie': cookie }
        }, {
            action: 'verify',
            razorpay_order_id: orderData.orderId,
            razorpay_payment_id: testPaymentId,
            razorpay_signature: validSignature
        });
        assert(dupVerifyRes.statusCode === 200, 'Duplicate verification call handled idempotently without error');
        const dupData = JSON.parse(dupVerifyRes.body);
        assert(dupData.success === true && dupData.message.includes('already verified'),
            'Idempotent response correctly indicates payment was already verified');

        console.log(`\n=== TEST RESULTS: ${passed} PASSED, ${failed} FAILED ===`);
        process.exit(failed > 0 ? 1 : 0);
    } catch (err) {
        console.error('Test execution failed with error:', err);
        process.exit(1);
    }
}

runRazorpayIntegrationTest();
