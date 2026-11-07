package com.psgitech.library.interfaces;

import com.psgitech.library.enums.PaymentMethodType;
import com.psgitech.library.model.FineRecord;
import com.psgitech.library.model.Payment;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Abstract Payment Method defining the processing template and contracts for payment handlers.
 * Demonstrates OOP Inheritance and the Template Method pattern.
 */
public abstract class PaymentMethod {

    /**
     * Gets the payment method type identifier.
     */
    public abstract PaymentMethodType getMethodType();

    /**
     * Prepares client-facing instructions, QR codes, deep links, or gateway tokens.
     */
    public abstract Map<String, Object> preparePaymentInstructions(Payment payment, FineRecord fine);

    /**
     * Validates student-provided submission data (e.g. UPI reference / UTR format).
     */
    public abstract void validateSubmission(String referenceData);
}

