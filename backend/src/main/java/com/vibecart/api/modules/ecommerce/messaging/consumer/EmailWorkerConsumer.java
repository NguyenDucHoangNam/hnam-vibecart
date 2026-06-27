package com.vibecart.api.modules.ecommerce.messaging.consumer;

import com.vibecart.api.config.KafkaTopicConfig;
import com.vibecart.api.modules.ecommerce.dto.event.OrderPaidEvent;
import com.vibecart.api.modules.jobs.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailWorkerConsumer {

    private final EmailService emailService;

    @KafkaListener(
            topics = KafkaTopicConfig.ORDER_PAID_TOPIC,
            groupId = "email-worker-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderPaidEvent(OrderPaidEvent event) {
        log.info("Received ORDER_PAID event for order: {}, sending email to: {}",
                event.getOrderId(), event.getUserEmail());

        try {
            sendOrderConfirmationEmail(event);
            log.info("Order confirmation email sent successfully to: {}", event.getUserEmail());
        } catch (Exception e) {
            log.error("Failed to send order confirmation email to: {}", event.getUserEmail(), e);
            throw new RuntimeException("Email sending failed", e);
        }
    }

    private void sendOrderConfirmationEmail(OrderPaidEvent event) {
        String subject = "[VibeCart] Xác nhận đơn hàng #" + event.getOrderId();

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.of("vi", "VN"));
        String formattedAmount = currencyFormat.format(event.getFinalAmount());

        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", event.getOrderId());
        variables.put("amount", formattedAmount);
        variables.put("transactionId", event.getPaymentTransactionId() != null ? event.getPaymentTransactionId() : "N/A");

        emailService.sendEmailWithTemplate(
                event.getUserEmail(),
                subject,
                "email/order-confirmation",
                variables
        );
    }
}
