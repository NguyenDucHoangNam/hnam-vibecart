package com.vibecart.api.modules.jobs.worker;

import com.vibecart.api.modules.jobs.dto.event.NotificationEvent;
import com.vibecart.api.modules.jobs.entity.BackgroundTask;
import com.vibecart.api.modules.jobs.entity.TaskStatus;
import com.vibecart.api.modules.jobs.repository.BackgroundTaskRepository;
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

import java.util.UUID;

@Component
public class NotificationKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationKafkaListener.class);

    private final EmailService emailService;
    private final BackgroundTaskRepository taskRepository;

    public NotificationKafkaListener(EmailService emailService, BackgroundTaskRepository taskRepository) {
        this.emailService = emailService;
        this.taskRepository = taskRepository;
    }

    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(delay = 5000, multiplier = 2.0),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            exclude = {IllegalArgumentException.class}
    )
    @KafkaListener(topics = "notification-events", groupId = "vibecart-notification-group")
    public void consumeNotificationEvent(@Payload NotificationEvent event) {
        log.info("Received Kafka notification event: ID={}, Recipient={}, TemplateType={}",
                event.getEventId(), event.getRecipientEmail(), event.getTemplateType());

        if (event.getRecipientEmail() == null || !event.getRecipientEmail().contains("@")) {
            log.warn("Invalid email format for event {}: {}. Sending directly to DLT (permanent error).",
                    event.getEventId(), event.getRecipientEmail());
            throw new IllegalArgumentException("Địa chỉ email người nhận không hợp lệ: " + event.getRecipientEmail());
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
        log.error("CRITICAL - KAFKA MESSAGE FAILED ALL RETRIES. Topic='{}', EventID='{}', Recipient='{}', Error: {}",
                topic, event.getEventId(), event.getRecipientEmail(), exceptionMessage);

        try {
            BackgroundTask failedTask = BackgroundTask.builder()
                    .id(UUID.randomUUID().toString())
                    .userId("SYSTEM")
                    .taskType("FAILED_NOTIFICATION")
                    .status(TaskStatus.FAILED)
                    .errorMessage("DLT Topic: " + topic + " | EventID: " + event.getEventId()
                            + " | Recipient: " + event.getRecipientEmail()
                            + " | Error: " + exceptionMessage)
                    .build();
            taskRepository.save(failedTask);
            log.info("Persisted failed notification event {} to background_tasks for manual review", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to persist DLT message to database. EventID={}", event.getEventId(), e);
        }
    }
}
