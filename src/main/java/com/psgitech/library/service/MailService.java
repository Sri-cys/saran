package com.psgitech.library.service;

public interface MailService {
    void send(String to, String subject, String body);
    void sendHtml(String to, String subject, String htmlBody);
}
