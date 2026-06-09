package com.vibecart.api.modules.shortener.event;

import com.vibecart.api.config.KafkaTopicConfig;
import com.vibecart.api.modules.ecommerce.event.OrderPaidEvent;
import com.vibecart.api.modules.ecommerce.event.OrderDeliveredEvent;
import com.vibecart.api.modules.ecommerce.event.OrderCancelledEvent;
import com.vibecart.api.modules.shortener.entity.Commission;
import com.vibecart.api.modules.shortener.repository.CommissionRepository;
import com.vibecart.api.modules.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Consumer xử lý các sự kiện đơn hàng (thanh toán, giao thành công, hủy) để tính toán hoa hồng tiếp thị.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AffiliateOrderConsumers {

    private final CommissionRepository commissionRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("10.00");

    @KafkaListener(
            topics = KafkaTopicConfig.ORDER_PAID_TOPIC,
            groupId = "affiliate-order-paid-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderPaidEvent(OrderPaidEvent event) {
        log.info("Affiliate: Received ORDER_PAID event for order: {}", event.getOrderId());


        String referralKey = "referral:order:" + event.getOrderId();
        String affiliateCreatorId = redisTemplate.opsForValue().get(referralKey);

        if (affiliateCreatorId == null || affiliateCreatorId.isBlank()) {
            log.info("Order {} does not have any active affiliate referral. Skipping commission.", event.getOrderId());
            return;
        }


        if (!userRepository.existsById(affiliateCreatorId)) {
            log.warn("Referral KOL creator {} not found in database. Skipping commission for order {}.", 
                    affiliateCreatorId, event.getOrderId());
            return;
        }


        Optional<Commission> existingOpt = commissionRepository.findByOrderId(event.getOrderId());
        if (existingOpt.isPresent()) {
            log.info("Commission for order {} was already processed. Skipping.", event.getOrderId());
            return;
        }


        BigDecimal subtotal = event.getFinalAmount();
        BigDecimal commissionAmount = subtotal.multiply(DEFAULT_COMMISSION_RATE)
                .divide(new BigDecimal("100.00"), 2, RoundingMode.HALF_UP);

        Commission commission = Commission.builder()
                .orderId(event.getOrderId())
                .creatorId(affiliateCreatorId)
                .subtotalAmount(subtotal)
                .commissionRate(DEFAULT_COMMISSION_RATE)
                .commissionAmount(commissionAmount)
                .status("PENDING")
                .build();

        commissionRepository.save(commission);
        log.info("Successfully recorded PENDING commission of {} VND for Creator {} from order {}.",
                commissionAmount, affiliateCreatorId, event.getOrderId());
    }

    @KafkaListener(
            topics = KafkaTopicConfig.ORDER_DELIVERED_TOPIC,
            groupId = "affiliate-order-delivered-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderDeliveredEvent(OrderDeliveredEvent event) {
        log.info("Affiliate: Received ORDER_DELIVERED event for order: {}", event.getOrderId());

        commissionRepository.findByOrderId(event.getOrderId()).ifPresent(commission -> {
            if ("PENDING".equals(commission.getStatus())) {
                commission.setStatus("APPROVED");
                commissionRepository.save(commission);
                log.info("Commission of {} VND for Creator {} has been APPROVED due to successful delivery of order {}.",
                        commission.getCommissionAmount(), commission.getCreatorId(), event.getOrderId());
            }
        });
    }

    @KafkaListener(
            topics = KafkaTopicConfig.ORDER_CANCELLED_TOPIC,
            groupId = "affiliate-order-cancelled-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCancelledEvent(OrderCancelledEvent event) {
        log.info("Affiliate: Received ORDER_CANCELLED event for order: {}", event.getOrderId());

        commissionRepository.findByOrderId(event.getOrderId()).ifPresent(commission -> {
            if ("PENDING".equals(commission.getStatus())) {
                commission.setStatus("REJECTED");
                commissionRepository.save(commission);
                log.info("Commission of {} VND for Creator {} has been REJECTED due to cancellation/refund of order {}.",
                        commission.getCommissionAmount(), commission.getCreatorId(), event.getOrderId());
            }
        });
    }
}
