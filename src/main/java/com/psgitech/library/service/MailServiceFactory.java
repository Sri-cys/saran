package com.psgitech.library.service;

import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MailServiceFactory {
    private static final Logger LOGGER = Logger.getLogger(MailServiceFactory.class.getName());
    private static MailService instance;

    public static synchronized MailService getMailService() {
        if (instance == null) {
            instance = createMailService();
        }
        return instance;
    }

    public static synchronized void setMailService(MailService customService) {
        instance = customService;
    }

    public static synchronized void reset() {
        instance = null;
    }

    private static MailService createMailService() {
        Properties props = new Properties();
        try (InputStream in = MailServiceFactory.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not load db.properties for mail config: " + e.getMessage());
        }

        String mode = System.getProperty("mail.mode", props.getProperty("mail.mode", "disabled")).trim().toLowerCase();
        if ("dev-outbox".equals(mode) || "outbox".equals(mode)) {
            LOGGER.info("Configuring DevOutboxMailService");
            return new DevOutboxMailService();
        } else if ("smtp".equals(mode)) {
            String host = System.getProperty("mail.host", props.getProperty("mail.host", "localhost"));
            int port = Integer.parseInt(System.getProperty("mail.port", props.getProperty("mail.port", "25")));
            String user = System.getProperty("mail.username", props.getProperty("mail.username", ""));
            String pass = System.getProperty("mail.password", props.getProperty("mail.password", ""));
            String from = System.getProperty("mail.from", props.getProperty("mail.from", "library@psgitech.ac.in"));
            boolean auth = Boolean.parseBoolean(System.getProperty("mail.smtp.auth", props.getProperty("mail.smtp.auth", "false")));
            boolean tls = Boolean.parseBoolean(System.getProperty("mail.smtp.starttls.enable", props.getProperty("mail.smtp.starttls.enable", "false")));
            return new SmtpMailService(host, port, user, pass, from, auth, tls);
        } else {
            return new DisabledMailService();
        }
    }
}
