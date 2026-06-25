package com.vibecart.api.modules.jobs.service;

import java.util.Map;

/**
 * Service gửi email thông qua JavaMailSender.
 */
public interface EmailService {
    void sendEmail(String to, String subject, String htmlContent);
    void sendEmailWithTemplate(String to, String subject, String templateName, Map<String, Object> variables);
}
