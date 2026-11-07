package com.psgitech.library.service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DevOutboxMailService implements MailService {
    private static final Logger LOGGER = Logger.getLogger(DevOutboxMailService.class.getName());
    private final File outboxDir;

    public DevOutboxMailService() {
        this.outboxDir = new File(System.getProperty("user.dir"), ".outbox");
        if (!outboxDir.exists()) {
            outboxDir.mkdirs();
        }
    }

    public DevOutboxMailService(File customDir) {
        this.outboxDir = customDir;
        if (!outboxDir.exists()) {
            outboxDir.mkdirs();
        }
    }

    @Override
    public void send(String to, String subject, String body) {
        writeEmailToFile(to, subject, body, false);
    }

    @Override
    public void sendHtml(String to, String subject, String htmlBody) {
        writeEmailToFile(to, subject, htmlBody, true);
    }

    private void writeEmailToFile(String to, String subject, String content, boolean isHtml) {
        String filename = System.currentTimeMillis() + "_" + to.replaceAll("[^a-zA-Z0-9.-]", "_") + ".txt";
        File file = new File(outboxDir, filename);
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("Timestamp: " + Instant.now());
            writer.println("To: " + to);
            writer.println("Subject: " + subject);
            writer.println("Type: " + (isHtml ? "text/html" : "text/plain"));
            writer.println("----------------------------------------");
            writer.println(content);
            LOGGER.info("[OUTBOX] Wrote email for " + to + " to " + file.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to write email to dev-outbox: " + file.getAbsolutePath(), e);
        }
    }
}

