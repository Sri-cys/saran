package com.psgitech.library.service;

import com.psgitech.library.config.FineConfig;
import com.psgitech.library.enums.PaymentMethodType;
import com.psgitech.library.interfaces.PaymentMethod;
import com.psgitech.library.model.FineRecord;
import com.psgitech.library.model.Payment;

import java.util.HashMap;
import java.util.Map;

/**
 * Concrete PaymentMethod implementing manual UPI collection with institutional QR
 * and backend staff verification against bank/UPI statements.
 */
public class ManualUpiPaymentMethod extends PaymentMethod {
    private final UpiService upiService;

    public ManualUpiPaymentMethod() {
        this.upiService = new UpiService();
    }

    public ManualUpiPaymentMethod(UpiService upiService) {
        this.upiService = (upiService != null) ? upiService : new UpiService();
    }

    @Override
    public PaymentMethodType getMethodType() {
        return PaymentMethodType.UPI_MANUAL;
    }

    @Override
    public Map<String, Object> preparePaymentInstructions(Payment payment, FineRecord fine) {
        Map<String, Object> instructions = new HashMap<>();
        String upiUri = upiService.buildUpiUri(payment.getAmount(), payment.getPaymentCode());

        instructions.put("method", getMethodType().name());
        instructions.put("upiId", FineConfig.getUpiId());
        instructions.put("payeeName", FineConfig.getPayeeName());
        instructions.put("amount", payment.getAmount().setScale(2).toPlainString());
        instructions.put("paymentCode", payment.getPaymentCode());
        instructions.put("qrImagePath", FineConfig.getQrImagePath());
        instructions.put("upiUri", upiUri);
        instructions.put("note", "Add payment code " + payment.getPaymentCode() + " in your UPI app payment note / remark.");
        return instructions;
    }

    @Override
    public void validateSubmission(String referenceData) {
        upiService.validateAndNormalizeReference(referenceData);
    }
}

