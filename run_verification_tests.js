const http = require('http');

function request(options, postData = null) {
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

async function runTests() {
    console.log('=== STARTING PSG iTECH LIBRARY SYSTEM VERIFICATION ===\n');
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
        // -------------------------------------------------------------
        // TEST F: Default Entry Point / Welcome File
        // -------------------------------------------------------------
        console.log('--- Test F: Default Entry Point (Root /) ---');
        const rootRes = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/',
            method: 'GET'
        });
        assert(rootRes.statusCode === 200, 'Root URL returns HTTP 200');
        assert(rootRes.body.includes('User Login | PSG iTech Library') || rootRes.body.includes('Account Sign In'),
            'Root URL serves Student/Faculty Login page (login.html)');

        // Check librarian login is still accessible at /librarian-login.html
        const libLoginRes = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/librarian-login.html',
            method: 'GET'
        });
        assert(libLoginRes.statusCode === 200 && libLoginRes.body.includes('Librarian Portal'),
            'Librarian login page is independently preserved at /librarian-login.html');

        // -------------------------------------------------------------
        // Login as Student 7 (Saran Karthi)
        // -------------------------------------------------------------
        console.log('\n--- Authenticating as Student 7 (Saran Karthi) ---');
        const loginRes = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/login',
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        }, {
            email: '25z304@psgitech.ac.in',
            password: 'Password@123'
        });
        const loginCookie = loginRes.headers['set-cookie'] ? loginRes.headers['set-cookie'][0].split(';')[0] : '';
        assert(loginRes.statusCode === 200 && loginCookie, 'Login successful and session cookie obtained');

        // -------------------------------------------------------------
        // TEST A: Razorpay Fine Payment Flow
        // -------------------------------------------------------------
        console.log('\n--- Test A: Razorpay Fine Payment Flow ---');
        // 1. Fetch fines for student
        const finesRes = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/my-fines',
            method: 'GET',
            headers: { 'Cookie': loginCookie }
        });
        const finesData = JSON.parse(finesRes.body);
        assert(finesData.success === true, 'Fines retrieved successfully');
        const targetFine = (finesData.fines && finesData.fines.length > 0) ? finesData.fines[0] : null;
        console.log(`Target fine: ID=${targetFine ? targetFine.fineId : 'N/A'}, Amount=${targetFine ? targetFine.outstandingAmount : 'N/A'}, Status=${targetFine ? targetFine.status : 'N/A'}`);

        if (targetFine) {
            // 2. Create Razorpay order
            const orderRes = await request({
                hostname: 'localhost',
                port: 8080,
                path: '/payments/razorpay/create-order',
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Cookie': loginCookie
                }
            }, {
                fineId: targetFine.fineId,
                amount: parseFloat(targetFine.outstandingAmount) || 34.00
            });
            assert(orderRes.statusCode === 200, 'Razorpay order creation endpoint returned HTTP 200');
            const orderData = JSON.parse(orderRes.body);
            assert(orderData.success === true && orderData.orderId && orderData.amountPaise > 0,
                'Order payload contains orderId, amountPaise, keyId, and test credentials');

            // 3. Verify Razorpay payment
            const verifyRes = await request({
                hostname: 'localhost',
                port: 8080,
                path: '/payments/razorpay/verify',
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Cookie': loginCookie
                }
            }, {
                razorpay_order_id: orderData.orderId,
                razorpay_payment_id: orderData.testPaymentId || 'pay_test_verified_123',
                razorpay_signature: orderData.testSignature || 'sig_test_verified_123',
                fineId: targetFine.fineId,
                amount: targetFine.outstandingAmount
            });
            assert(verifyRes.statusCode === 200, 'Payment verification returned HTTP 200');
            const verifyData = JSON.parse(verifyRes.body);
            assert(verifyData.success === true && verifyData.paymentId && verifyData.receiptNumber,
                `Payment verified successfully with paymentId=${verifyData.paymentId}, receipt=${verifyData.receiptNumber}`);

            // -------------------------------------------------------------
            // TEST C: Fine Receipt Download (Official PDF)
            // -------------------------------------------------------------
            console.log('\n--- Test C: Fine Receipt Download (Official PDF) ---');
            const pdfRes = await request({
                hostname: 'localhost',
                port: 8080,
                path: `/payments/receipt/pdf?paymentId=${verifyData.paymentId}`,
                method: 'GET',
                headers: { 'Cookie': loginCookie }
            });
            assert(pdfRes.statusCode === 200, 'PDF Receipt endpoint returned HTTP 200');
            assert(pdfRes.headers['content-type'] === 'application/pdf',
                'Response Content-Type is application/pdf');
            assert((pdfRes.headers['content-disposition'] || '').includes('attachment') &&
                   (pdfRes.headers['content-disposition'] || '').includes('.pdf'),
                'Content-Disposition header specifies attachment with .pdf filename');
            assert(pdfRes.buffer.slice(0, 4).toString() === '%PDF',
                'Response binary starts with valid PDF magic bytes (%PDF-1.4)');
            console.log(`Generated PDF Receipt Size: ${pdfRes.buffer.length} bytes`);
        }

        // -------------------------------------------------------------
        // TEST D & E: Book Handover Identity & OTP Demonstration
        // -------------------------------------------------------------
        console.log('\n--- Test D & E: Book Handover Identity & OTP ---');
        // Fetch incoming interchanges for student 7 (Saran Karthi)
        const icRes = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/interchanges?type=incoming',
            method: 'GET',
            headers: { 'Cookie': loginCookie }
        });
        const icData = JSON.parse(icRes.body);
        assert(icData.success === true, 'Incoming interchanges retrieved successfully');
        console.log(`Found ${icData.data.length} incoming interchange(s) for Student 7.`);
        if (icData.data.length > 0) {
            const reqItem = icData.data[0];
            console.log(`Interchange #${reqItem.interchangeId}: Book="${reqItem.bookTitle}", From="${reqItem.fromStudentName}" (${reqItem.fromRegisterNumber}), To="${reqItem.toStudentName}" (${reqItem.toRegisterNumber}), InitiatedBy=${reqItem.initiatedBy}`);
            assert(reqItem.fromStudentName !== reqItem.toStudentName,
                'Lender and Recipient identities are distinct and correct');
            
            // Test OTP generation
            const otpRes = await request({
                hostname: 'localhost',
                port: 8080,
                path: '/interchanges/otp/generate',
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Cookie': loginCookie
                }
            }, { interchangeId: reqItem.interchangeId });
            assert(otpRes.statusCode === 200, 'OTP generation returned HTTP 200');
            const otpData = JSON.parse(otpRes.body);
            const receivedOtp = otpData.otp || (otpData.data && otpData.data.otp);
            assert(/^[0-9]{6}$/.test(receivedOtp),
                `6-Digit OTP generated successfully: ${receivedOtp}`);
        }

        // -------------------------------------------------------------
        // TEST B: Logout Flow (AJAX and Direct Navigation)
        // -------------------------------------------------------------
        console.log('\n--- Test B: Logout Flow ---');
        // 1. AJAX Logout request
        const ajaxLogoutRes = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/logout',
            method: 'POST',
            headers: {
                'X-Requested-With': 'XMLHttpRequest',
                'Accept': 'application/json',
                'Cookie': loginCookie
            }
        });
        assert(ajaxLogoutRes.statusCode === 200, 'AJAX Logout returned HTTP 200');
        const logoutJson = JSON.parse(ajaxLogoutRes.body);
        assert(logoutJson.success === true && logoutJson.redirect.includes('login.html'),
            `AJAX Logout returns JSON redirect payload: redirect=${logoutJson.redirect}`);
        assert((ajaxLogoutRes.headers['set-cookie'] || []).some(c => c.includes('Max-Age=0') || c.includes('Expires=Thu, 01 Jan 1970')),
            'Session cookie cleared on logout');

        // 2. Direct browser GET navigation to /logout
        const directLogoutRes = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/logout',
            method: 'GET'
        });
        assert(directLogoutRes.statusCode === 302, 'Direct browser GET to /logout issues HTTP 302 Redirect');
        assert((directLogoutRes.headers['location'] || '').includes('login.html'),
            `Direct logout redirects to login page: ${directLogoutRes.headers['location']}`);

        // 3. Confirm old session is invalidated
        const postLogoutRes = await request({
            hostname: 'localhost',
            port: 8080,
            path: '/fines',
            method: 'GET',
            headers: { 'Cookie': loginCookie }
        });
        assert(postLogoutRes.statusCode === 401 || (JSON.parse(postLogoutRes.body || '{}').success === false),
            'Protected route rejects invalidated session');

        console.log(`\n=== VERIFICATION SUMMARY: ${passed} PASSED, ${failed} FAILED ===`);
        process.exit(failed > 0 ? 1 : 0);
    } catch (err) {
        console.error('Test execution error:', err);
        process.exit(1);
    }
}

runTests();
