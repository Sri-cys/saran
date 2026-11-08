package com.psgitech.library.service;

import com.psgitech.library.config.NotificationPolicy;
import com.psgitech.library.enums.NotificationType;

import java.io.InputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Institutional Notification Template Service.
 * Formats notification titles, in-app messages, email subjects, email bodies,
 * and validates/resolves target action URLs.
 * Enforces sanitization, length caps, and zero sensitive data leakage rules.
 */
public class NotificationTemplateService {
    private static final Logger LOGGER = Logger.getLogger(NotificationTemplateService.class.getName());

    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final String DEFAULT_BASE_URL = "http://localhost:8081/psgitech-library";

    private static final Set<String> WHITELISTED_PATHS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "my-reservations.html",
            "reservations.html",
            "my-borrowings.html",
            "borrowings.html",
            "borrowing-management.html",
            "my-fines.html",
            "fines.html",
            "notifications.html",
            "profile.html",
            "login.html",
            "admin-dashboard.html",
            "smart-discovery.html"
    )));

    private final String baseUrl;

    public NotificationTemplateService() {
        this.baseUrl = loadBaseUrl();
    }

    public NotificationTemplateService(String baseUrl) {
        this.baseUrl = baseUrl != null ? sanitizeBaseUrl(baseUrl) : DEFAULT_BASE_URL;
    }

    private static String loadBaseUrl() {
        Properties props = new Properties();
        try (InputStream is = NotificationTemplateService.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load db.properties for app.base.url", e);
        }
        String configured = System.getProperty("app.base.url", props.getProperty("app.base.url", DEFAULT_BASE_URL));
        return sanitizeBaseUrl(configured);
    }

    private static String sanitizeBaseUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public static class RenderedTemplate {
        private final String title;
        private final String message;
        private final String emailSubject;
        private final String emailBody;
        private final String actionUrl;

        public RenderedTemplate(String title, String message, String emailSubject, String emailBody, String actionUrl) {
            this.title = title;
            this.message = message;
            this.emailSubject = emailSubject;
            this.emailBody = emailBody;
            this.actionUrl = actionUrl;
        }

        public String getTitle() {
            return title;
        }

        public String getMessage() {
            return message;
        }

        public String getEmailSubject() {
            return emailSubject;
        }

        public String getEmailBody() {
            return emailBody;
        }

        public String getActionUrl() {
            return actionUrl;
        }
    }

    public RenderedTemplate render(NotificationType type, Map<String, Object> rawParams) {
        Map<String, String> params = sanitizeParams(rawParams);
        String defaultPath = NotificationPolicy.getRule(type).getActionPath();
        String actionUrl = resolveActionUrl(defaultPath);

        String title;
        String message;
        String emailSubject;
        String emailBody;

        String bookTitle = params.getOrDefault("bookTitle", "Book");
        String dueDate = params.getOrDefault("dueDate", "");
        String pickupDeadline = params.getOrDefault("pickupDeadline", "");
        String amount = params.getOrDefault("amount", "0.00");
        String queuePosition = params.getOrDefault("queuePosition", "1");
        String reason = params.getOrDefault("reason", "Policy constraints");
        String utrMasked = params.getOrDefault("utrMasked", "****");

        switch (type) {
            case RESERVATION_CREATED:
                title = "Reservation Placed";
                message = "Your reservation for '" + bookTitle + "' was placed successfully. Queue position: #" + queuePosition + ".";
                emailSubject = "PSG iTech Library - Reservation Confirmed: " + bookTitle;
                emailBody = buildEmailBody(title, message, actionUrl, "View Reservations");
                break;

            case ALLOCATION_READY:
                title = "Book Ready for Pickup";
                message = "A copy of '" + bookTitle + "' is reserved and ready for pickup until " + pickupDeadline + ".";
                emailSubject = "PSG iTech Library - Book Ready for Pickup: " + bookTitle;
                emailBody = buildEmailBody(title, message, actionUrl, "View Pickup Details");
                break;

            case ALLOCATION_REMINDER:
                title = "Pickup Deadline Reminder";
                message = "Reminder: Your allocated copy of '" + bookTitle + "' will be released after " + pickupDeadline + " if not collected.";
                emailSubject = "PSG iTech Library - Reminder: Collect your reserved copy of " + bookTitle;
                emailBody = buildEmailBody(title, message, actionUrl, "Collect Book");
                break;

            case RESERVATION_CANCELLED:
                title = "Reservation Cancelled";
                message = "Your reservation for '" + bookTitle + "' has been cancelled.";
                emailSubject = "PSG iTech Library - Reservation Cancelled: " + bookTitle;
                emailBody = buildEmailBody(title, message, actionUrl, "View Reservations");
                break;

            case RESERVATION_EXPIRED:
                title = "Reservation Expired";
                message = "Your reservation allocation for '" + bookTitle + "' has expired and the book has been released.";
                emailSubject = "PSG iTech Library - Reservation Allocation Expired: " + bookTitle;
                emailBody = buildEmailBody(title, message, actionUrl, "View Reservations");
                break;

            case LOAN_ISSUED:
                title = "Book Issued";
                message = "Copy of '" + bookTitle + "' has been issued to you. Due date: " + dueDate + ".";
                emailSubject = "PSG iTech Library - Book Issued: " + bookTitle;
                emailBody = buildEmailBody(title, message, actionUrl, "View Active Loans");
                break;

            case LOAN_RETURNED:
                title = "Book Returned";
                message = "Copy of '" + bookTitle + "' returned successfully. Thank you!";
                emailSubject = "PSG iTech Library - Return Confirmation: " + bookTitle;
                emailBody = buildEmailBody(title, message, actionUrl, "View Borrowing History");
                break;

            case LOAN_RENEWED:
                title = "Loan Renewed";
                message = "Loan for '" + bookTitle + "' renewed successfully. New due date: " + dueDate + ".";
                emailSubject = "PSG iTech Library - Loan Renewed: " + bookTitle;
                emailBody = buildEmailBody(title, message, actionUrl, "View Active Loans");
                break;

            case RENEWAL_REJECTED:
                title = "Renewal Unsuccessful";
                message = "Renewal for '" + bookTitle + "' could not be completed: " + reason + ".";
                emailSubject = "PSG iTech Library - Renewal Not Allowed: " + bookTitle;
                emailBody = buildEmailBody(title, message, actionUrl, "View Active Loans");
                break;

            case DUE_SOON:
                title = "Loan Due Soon";
                message = "Reminder: '" + bookTitle + "' is due on " + dueDate + ". Please return or renew on time.";
                emailSubject = "PSG iTech Library - Upcoming Due Date: " + bookTitle;
                emailBody = buildEmailBody(title, message, actionUrl, "Renew or Return");
                break;

            case OVERDUE:
                title = "Book Overdue";
                message = "Your loan for '" + bookTitle + "' was due on " + dueDate + " and is now overdue. Please return immediately.";
                emailSubject = "PSG iTech Library - OVERDUE NOTICE: " + bookTitle;
                emailBody = buildEmailBody(title, message, actionUrl, "View Overdue Loan");
                break;

            case FINE_CREATED:
                title = "Fine Assessed";
                message = "A fine of Rs. " + amount + " has been assessed for '" + bookTitle + "'.";
                emailSubject = "PSG iTech Library - Fine Assessed: Rs. " + amount;
                emailBody = buildEmailBody(title, message, actionUrl, "Pay Fine");
                break;

            case FINE_WAIVED:
                title = "Fine Waived";
                message = "A fine of Rs. " + amount + " for '" + bookTitle + "' has been waived.";
                emailSubject = "PSG iTech Library - Fine Waiver Notice";
                emailBody = buildEmailBody(title, message, actionUrl, "View Fines");
                break;

            case PAYMENT_SUBMITTED:
                title = "Payment Submitted";
                message = "Your payment of Rs. " + amount + " (ref ending in " + utrMasked + ") was submitted and is pending verification.";
                emailSubject = "PSG iTech Library - Payment Received for Verification";
                emailBody = buildEmailBody(title, message, actionUrl, "View Payment Status");
                break;

            case PAYMENT_PENDING_VERIFICATION:
                title = "Payment Verification Pending";
                message = "A student payment of Rs. " + amount + " (ref ending in " + utrMasked + ") requires staff verification.";
                emailSubject = "PSG iTech Library Staff - Payment Pending Verification";
                emailBody = buildEmailBody(title, message, actionUrl, "Review Payments");
                break;

            case PAYMENT_VERIFIED:
                title = "Payment Verified";
                message = "Your payment of Rs. " + amount + " has been verified and credited to your account.";
                emailSubject = "PSG iTech Library - Payment Verified: Rs. " + amount;
                emailBody = buildEmailBody(title, message, actionUrl, "View Fines & Receipts");
                break;

            case PAYMENT_REJECTED:
                title = "Payment Rejected";
                message = "Your payment submission of Rs. " + amount + " was rejected: " + reason + ".";
                emailSubject = "PSG iTech Library - Payment Verification Rejected";
                emailBody = buildEmailBody(title, message, actionUrl, "Resolve Payment");
                break;

            case REFUND_RECORDED:
                title = "Refund Processed";
                message = "A refund of Rs. " + amount + " has been recorded for your account.";
                emailSubject = "PSG iTech Library - Refund Recorded: Rs. " + amount;
                emailBody = buildEmailBody(title, message, actionUrl, "View Financial History");
                break;

            case INTERCHANGE_REQUEST_RECEIVED:
                title = "Interchange Request";
                message = "You received a book interchange request for '" + bookTitle + "'.";
                emailSubject = "PSG iTech Library - Book Interchange Request: " + bookTitle;
                emailBody = buildEmailBody(title, message, actionUrl, "Respond to Interchange");
                break;

            case INTERCHANGE_COMPLETED:
                title = "Interchange Completed";
                message = "The book interchange for '" + bookTitle + "' was completed successfully.";
                emailSubject = "PSG iTech Library - Interchange Completed: " + bookTitle;
                emailBody = buildEmailBody(title, message, actionUrl, "View Borrowings");
                break;

            case INTERCHANGE_REJECTED:
                title = "Interchange Declined";
                message = "The book interchange request for '" + bookTitle + "' was declined.";
                emailSubject = "PSG iTech Library - Interchange Declined: " + bookTitle;
                emailBody = buildEmailBody(title, message, actionUrl, "View Borrowings");
                break;

            case INTERCHANGE_CANCELLED:
                title = "Interchange Cancelled";
                message = "The book interchange request for '" + bookTitle + "' was cancelled.";
                emailSubject = "PSG iTech Library - Interchange Cancelled: " + bookTitle;
                emailBody = buildEmailBody(title, message, actionUrl, "View Borrowings");
                break;

            case INTERCHANGE_INVALIDATED:
                title = "Interchange Invalidated";
                message = "An interchange request for '" + bookTitle + "' was cancelled because the copy was returned.";
                emailSubject = "PSG iTech Library - Interchange Invalidated: " + bookTitle;
                emailBody = buildEmailBody(title, message, actionUrl, "View Borrowings");
                break;

            case PASSWORD_CHANGED:
                title = "Security Alert: Password Changed";
                message = "Your library account password was changed. If you did not make this change, please contact library administration immediately.";
                emailSubject = "PSG iTech Library - Security Alert: Password Changed";
                emailBody = buildEmailBody(title, message, actionUrl, "Review Account");
                break;

            case DISCOVERY_NEW_BOOK:
                title = "New Arrival in Catalog";
                message = "A new book matching your reading interests was added: '" + bookTitle + "'.";
                emailSubject = "PSG iTech Library - New Arrival: " + bookTitle;
                emailBody = buildEmailBody(title, message, actionUrl, "Explore Catalog");
                break;

            default:
                title = "Library Notification";
                message = "You have an update from the PSG iTech Library.";
                emailSubject = "PSG iTech Library Notification";
                emailBody = buildEmailBody(title, message, actionUrl, "Open Library");
                break;
        }

        title = sanitizeText(title, MAX_TITLE_LENGTH);
        message = sanitizeText(message, MAX_MESSAGE_LENGTH);
        emailSubject = stripNewlines(emailSubject);

        return new RenderedTemplate(title, message, emailSubject, emailBody, actionUrl);
    }

    public String resolveActionUrl(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return baseUrl + "/notifications.html";
        }
        String clean = relativePath.trim();
        while (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        int queryIdx = clean.indexOf('?');
        String pathPart = queryIdx >= 0 ? clean.substring(0, queryIdx) : clean;
        if (!WHITELISTED_PATHS.contains(pathPart)) {
            return baseUrl + "/notifications.html";
        }
        return baseUrl + "/" + clean;
    }

    public static String sanitizeText(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        // Strip control characters (\x00-\x1F except standard space, tab, newline, and \x7F)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= 32 && c != 127) {
                sb.append(c);
            } else if (c == ' ' || c == '\t') {
                sb.append(' ');
            }
        }
        String sanitized = sb.toString().trim();
        if (sanitized.length() > maxLength) {
            return sanitized.substring(0, maxLength);
        }
        return sanitized;
    }

    public static String stripNewlines(String input) {
        if (input == null) return "";
        return input.replace('\r', ' ').replace('\n', ' ').trim();
    }

    public static String maskUtr(String utr) {
        if (utr == null || utr.trim().isEmpty()) {
            return "****";
        }
        String clean = utr.trim();
        if (clean.length() <= 4) {
            return "****" + clean;
        }
        return "****" + clean.substring(clean.length() - 4);
    }

    private static Map<String, String> sanitizeParams(Map<String, Object> raw) {
        if (raw == null) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String key = entry.getKey();
            String val = String.valueOf(entry.getValue());
            if ("utr".equalsIgnoreCase(key) || "referenceNumber".equalsIgnoreCase(key)) {
                map.put("utrMasked", maskUtr(val));
            } else if ("password".equalsIgnoreCase(key) || "token".equalsIgnoreCase(key) || "otp".equalsIgnoreCase(key)) {
                // Never allow passwords or tokens into templates
                map.put(key, "[PROTECTED]");
            } else {
                map.put(key, sanitizeText(val, 200));
            }
        }
        return map;
    }

    private static String buildEmailBody(String heading, String message, String actionUrl, String buttonText) {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <style>\n" +
                "    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; color: #1f2937; margin: 0; padding: 24px; background-color: #f3f4f6; }\n" +
                "    .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 8px; border: 1px solid #e5e7eb; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); }\n" +
                "    .header { background-color: #1e3a8a; color: #ffffff; padding: 20px 24px; font-size: 18px; font-weight: bold; }\n" +
                "    .content { padding: 24px; }\n" +
                "    .content h2 { margin-top: 0; color: #111827; font-size: 20px; }\n" +
                "    .content p { margin: 16px 0; color: #374151; font-size: 15px; }\n" +
                "    .btn-container { margin-top: 24px; }\n" +
                "    .btn { display: inline-block; background-color: #2563eb; color: #ffffff !important; text-decoration: none; padding: 10px 20px; border-radius: 6px; font-weight: 500; font-size: 14px; }\n" +
                "    .footer { background-color: #f9fafb; padding: 16px 24px; font-size: 12px; color: #6b7280; border-top: 1px solid #e5e7eb; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"container\">\n" +
                "    <div class=\"header\">PSG iTech Library</div>\n" +
                "    <div class=\"content\">\n" +
                "      <h2>" + escapeHtml(heading) + "</h2>\n" +
                "      <p>" + escapeHtml(message) + "</p>\n" +
                "      <div class=\"btn-container\">\n" +
                "        <a href=\"" + escapeHtml(actionUrl) + "\" class=\"btn\">" + escapeHtml(buttonText) + "</a>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "    <div class=\"footer\">\n" +
                "      This is an automated notification from the PSG iTech Library System. Please do not reply directly to this email.\n" +
                "    </div>\n" +
                "  </div>\n" +
                "</body>\n" +
                "</html>";
    }

    private static String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
