package com.psgitech.library.service;

import java.util.logging.Logger;

public class DisabledMailService implements MailService {
    private static final Logger LOGGER = Logger.getLogger(DisabledMailService.class.getName());

    @Override
    public void send(String to, String subject, String body) {
        LOGGER.info("[DISABLED MAIL SERVICE] Email to: " + to + " | Subject: " + subject);
    }

    @Override
    public void sendHtml(String to, String subject, String htmlBody) {
        LOGGER.info("[DISABLED MAIL SERVICE] HTML Email to: " + to + " | Subject: " + subject);
    }
}

