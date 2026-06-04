package com.vibecart.api.modules.ecommerce.event;

import com.vibecart.api.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.text.NumberFormat;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailWorkerConsumer {

    private final JavaMailSender mailSender;

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
        } catch (MailException | MessagingException e) {
            log.error("Failed to send order confirmation email to: {}", event.getUserEmail(), e);
            throw new RuntimeException("Email sending failed", e); // Trigger Kafka retry
        }
    }

    private void sendOrderConfirmationEmail(OrderPaidEvent event) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(event.getUserEmail());
        helper.setSubject("[VibeCart] Xác nhận đơn hàng #" + event.getOrderId());

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.of("vi", "VN"));
        String formattedAmount = currencyFormat.format(event.getFinalAmount());

        String htmlContent = """
                <html>
                <body style="font-family: 'Segoe UI', Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <div style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 30px; text-align: center;">
                        <h1 style="color: white; margin: 0;">🎉 Đặt hàng thành công!</h1>
                    </div>
                    <div style="padding: 30px; background: #f9f9f9;">
                        <p style="font-size: 16px;">Cảm ơn bạn đã mua hàng tại <strong>VibeCart</strong>!</p>
                        <div style="background: white; padding: 20px; border-radius: 8px; margin: 20px 0;">
                            <p><strong>Mã đơn hàng:</strong> %s</p>
                            <p><strong>Tổng thanh toán:</strong> <span style="color: #667eea; font-size: 20px;">%s</span></p>
                            <p><strong>Mã giao dịch:</strong> %s</p>
                        </div>
                        <p style="color: #666;">Đơn hàng đang được xử lý. Chúng tôi sẽ thông báo khi đơn hàng được giao cho đơn vị vận chuyển.</p>
                    </div>
                    <div style="background: #333; padding: 15px; text-align: center; color: #999;">
                        <p>© 2026 VibeCart - Social E-Commerce Platform</p>
                    </div>
                </body>
                </html>
                """.formatted(
                event.getOrderId(),
                formattedAmount,
                event.getPaymentTransactionId() != null ? event.getPaymentTransactionId() : "N/A"
        );

        helper.setText(htmlContent, true);
        mailSender.send(message);
    }
}
