package com.psgitech.library.service;

/**
 * Service interface for institutional email dispatches.
 * Delegates to the configured MailService implementation (SMTP, DevOutbox, or Test).
 */
public interface EmailService {

    void sendEmail(String toEmail, String subject, String body);

    static EmailService getInstance() {
        return (toEmail, subject, body) -> {
            MailService ms = MailServiceFactory.getMailService();
            if (ms != null) {
                ms.send(toEmail, subject, body);
            }
        };
    }
}
