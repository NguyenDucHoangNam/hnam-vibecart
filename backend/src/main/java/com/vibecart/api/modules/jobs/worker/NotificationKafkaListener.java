package com.vibecart.api.modules.jobs.worker;

import com.vibecart.api.modules.jobs.dto.NotificationEvent;
import com.vibecart.api.modules.jobs.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka listener xử lý sự kiện gửi thông báo email với cơ chế retry và DLT.
 */
@Component
public class NotificationKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaListener.class);

    private final EmailService emailService;

    public NotificationKafkaListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @RetryableTopic(attempts = "4", backOff = @BackOff(delay = 5000, multiplier = 2.0), dltStrategy = DltStrategy.FAIL_ON_ERROR, topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(topics = "notification-events", groupId = "vibecart-notification-group")
    public void consumeNotificationEvent(@Payload NotificationEvent event) {
        log.info("Received Kafka notification event: ID={}, Recipient={}, TemplateType={}", 
                event.getEventId(), event.getRecipientEmail(), event.getTemplateType());

        if (event.getRecipientEmail() == null || !event.getRecipientEmail().contains("@")) {
            log.warn("Invalid email format for event {}: {}. Triggering Kafka retry...", event.getEventId(),
                     event.getRecipientEmail());
            throw new IllegalArgumentException("Địa chỉ email người nhận không hợp lệ");
        }

        if (event.getTemplateType() != null && !event.getTemplateType().trim().isEmpty()) {
            String templateName = getTemplatePath(event.getTemplateType());
            emailService.sendEmailWithTemplate(
                    event.getRecipientEmail(), 
                    event.getSubject(), 
                    templateName, 
                    event.getTemplateParams()
            );
        } else {
            emailService.sendEmail(event.getRecipientEmail(), event.getSubject(), event.getBody());
        }
    }

    private String getTemplatePath(String templateType) {
        return switch (templateType.toUpperCase()) {
            case "REGISTRATION_OTP" -> "email/registration-otp";
            case "FORGOT_PASSWORD" -> "email/forgot-password";
            default -> throw new IllegalArgumentException("Unknown template type: " + templateType);
        };
    }

    @DltHandler
    public void handleDeadLetterMessage(NotificationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        log.error("CRITICAL - KAFKA MESSAGE FAILED ALL RETRIES. Directed to DLT topic '{}'. Event ID='{}', Error: {}",
                topic, event.getEventId(), exceptionMessage);

    }
}
