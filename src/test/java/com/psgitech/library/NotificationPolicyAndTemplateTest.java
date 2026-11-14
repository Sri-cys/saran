package com.psgitech.library;

import com.psgitech.library.config.NotificationPolicy;
import com.psgitech.library.enums.EmailPolicy;
import com.psgitech.library.enums.InAppPolicy;
import com.psgitech.library.enums.NotificationCategory;
import com.psgitech.library.enums.NotificationType;
import com.psgitech.library.service.NotificationTemplateService;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Layer 1 Unit Tests for Notification Policies, Template Rendering, and Sanitization (Feature 11).
 */
public class NotificationPolicyAndTemplateTest {

    private final NotificationTemplateService templateService = new NotificationTemplateService("http://localhost:8081/psgitech-library");

    @Test
    public void testPolicy_EveryNotificationTypeHasConfiguredRule() {
        for (NotificationType type : NotificationType.values()) {
            NotificationPolicy.PolicyRule rule = NotificationPolicy.getRule(type);
            assertNotNull("Rule must exist for type: " + type, rule);
            assertNotNull("Category must exist for type: " + type, rule.getCategory());
            assertNotNull("InAppPolicy must exist for type: " + type, rule.getInAppPolicy());
            assertNotNull("EmailPolicy must exist for type: " + type, rule.getEmailPolicy());
            assertNotNull("Action path must exist for type: " + type, rule.getActionPath());
        }
    }

    @Test
    public void testPolicy_LockedCategoriesEnforceCompliance() {
        assertTrue("FINES_PAYMENTS email must be locked", NotificationPolicy.isEmailLocked(NotificationCategory.FINES_PAYMENTS));
        assertTrue("SYSTEM email must be locked", NotificationPolicy.isEmailLocked(NotificationCategory.SYSTEM));
        assertFalse("RESERVATION email should not be locked", NotificationPolicy.isEmailLocked(NotificationCategory.RESERVATION));
        assertFalse("BORROWING email should not be locked", NotificationPolicy.isEmailLocked(NotificationCategory.BORROWING));

        assertNotNull(NotificationPolicy.getLockReason(NotificationCategory.FINES_PAYMENTS));
        assertNotNull(NotificationPolicy.getLockReason(NotificationCategory.SYSTEM));
    }

    @Test
    public void testSanitization_ControlCharactersStrippedAndLengthCapped() {
        String dirtyText = "Hello\u0000World!\u001F Test\t\nLine";
        String clean = NotificationTemplateService.sanitizeText(dirtyText, 20);
        assertFalse("Must not contain NUL char", clean.contains("\u0000"));
        assertFalse("Must not contain control char", clean.contains("\u001F"));
        assertTrue("Length must be capped at 20", clean.length() <= 20);
    }

    @Test
    public void testSanitization_EmailSubjectHeaderInjectionPrevented() {
        String dirtySubject = "Notification Header\r\nBcc: victim@example.com\nContent-Type: text/html";
        String safeSubject = NotificationTemplateService.stripNewlines(dirtySubject);
        assertFalse("Subject must not contain CR", safeSubject.contains("\r"));
        assertFalse("Subject must not contain LF", safeSubject.contains("\n"));
        assertTrue("Newlines converted to space", safeSubject.contains("Notification Header  Bcc: victim@example.com Content-Type: text/html"));
    }

    @Test
    public void testWhitelistedUrls_InternalAllowedAndExternalRejected() {
        String validUrl = templateService.resolveActionUrl("my-borrowings.html");
        assertEquals("http://localhost:8081/psgitech-library/my-borrowings.html", validUrl);

        String fineUrl = templateService.resolveActionUrl("/my-fines.html");
        assertEquals("http://localhost:8081/psgitech-library/my-fines.html", fineUrl);

        // Non-whitelisted or phishing path falls back to notifications.html
        String badUrl = templateService.resolveActionUrl("https://evil-phishing.com/steal-creds");
        assertEquals("http://localhost:8081/psgitech-library/notifications.html", badUrl);

        String randomScript = templateService.resolveActionUrl("admin-exploit.jsp");
        assertEquals("http://localhost:8081/psgitech-library/notifications.html", randomScript);
    }

    @Test
    public void testAntiDataLeak_UtrMaskedAndPasswordsBlocked() {
        assertEquals("****1234", NotificationTemplateService.maskUtr("123456781234"));
        assertEquals("****9876", NotificationTemplateService.maskUtr("9876"));
        assertEquals("****", NotificationTemplateService.maskUtr(""));
        assertEquals("****", NotificationTemplateService.maskUtr(null));

        Map<String, Object> params = new HashMap<>();
        params.put("utr", "UPI987654321012");
        params.put("password", "SecretPassword123!");
        params.put("token", "jwt-reset-token-abc");
        params.put("amount", "50.00");

        NotificationTemplateService.RenderedTemplate rendered = templateService.render(NotificationType.PAYMENT_SUBMITTED, params);
        assertTrue("Message must contain masked UTR", rendered.getMessage().contains("1012"));
        assertFalse("Message must NOT contain full UTR", rendered.getMessage().contains("UPI987654321012"));
        assertFalse("Message must NOT contain password", rendered.getMessage().contains("SecretPassword123!"));
        assertFalse("Message must NOT contain token", rendered.getMessage().contains("jwt-reset-token"));
    }

    @Test
    public void testTemplateRendering_AllEventTypesProduceNonEmptyContent() {
        Map<String, Object> params = new HashMap<>();
        params.put("bookTitle", "Clean Architecture");
        params.put("dueDate", "2026-12-01");
        params.put("pickupDeadline", "2026-11-10 18:00");
        params.put("amount", "25.00");
        params.put("queuePosition", "2");

        for (NotificationType type : NotificationType.values()) {
            NotificationTemplateService.RenderedTemplate res = templateService.render(type, params);
            assertNotNull("Title cannot be null for " + type, res.getTitle());
            assertFalse("Title cannot be empty for " + type, res.getTitle().trim().isEmpty());
            assertNotNull("Message cannot be null for " + type, res.getMessage());
            assertFalse("Message cannot be empty for " + type, res.getMessage().trim().isEmpty());
            assertNotNull("Subject cannot be null for " + type, res.getEmailSubject());
            assertNotNull("Body cannot be null for " + type, res.getEmailBody());
            assertNotNull("Action URL cannot be null for " + type, res.getActionUrl());
        }
    }
}

