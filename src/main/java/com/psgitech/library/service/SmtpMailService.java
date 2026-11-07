package com.psgitech.library.service;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SmtpMailService implements MailService {
    private static final Logger LOGGER = Logger.getLogger(SmtpMailService.class.getName());

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String fromAddress;
    private final boolean auth;
    private final boolean starttls;

    public SmtpMailService(String host, int port, String username, String password,
                           String fromAddress, boolean auth, boolean starttls) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.fromAddress = fromAddress;
        this.auth = auth;
        this.starttls = starttls;
    }

    private Session createSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));

        if (auth && username != null && !username.isEmpty()) {
            return Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });
        }
        return Session.getInstance(props);
    }

    @Override
    public void send(String to, String subject, String body) {
        sendInternal(to, subject, body, "text/plain; charset=UTF-8");
    }

    @Override
    public void sendHtml(String to, String subject, String htmlBody) {
        sendInternal(to, subject, htmlBody, "text/html; charset=UTF-8");
    }

    private void sendInternal(String to, String subject, String content, String contentType) {
        try {
            Session session = createSession();
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromAddress));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject, "UTF-8");
            message.setContent(content, contentType);

            Transport.send(message);
            LOGGER.info("Email successfully sent via SMTP to: " + to);
        } catch (MessagingException e) {
            LOGGER.log(Level.SEVERE, "Failed to send SMTP email to: " + to, e);
            throw new RuntimeException("Email delivery failed: " + e.getMessage(), e);
        }
    }
}

