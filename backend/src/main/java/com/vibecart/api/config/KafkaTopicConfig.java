package com.vibecart.api.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Cấu hình khởi tạo các Topic Kafka cho hệ thống.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaTopicConfig {

    public static final String PRODUCT_SYNC_TOPIC = "product-sync-topic";
    public static final String ORDER_PAID_TOPIC = "order-paid-topic";
    public static final String AFFILIATE_CLICKS_TOPIC = "affiliate-clicks-topic";
    public static final String ORDER_DELIVERED_TOPIC = "order-delivered-topic";
    public static final String ORDER_CANCELLED_TOPIC = "order-cancelled-topic";
    public static final String NOTIFICATION_EVENTS_TOPIC = "notification-events";

    /**
     * Topic đồng bộ thông tin sản phẩm.
     */
    @Bean
    public NewTopic productSyncTopic() {
        return TopicBuilder.name(PRODUCT_SYNC_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Topic xử lý sự kiện đơn hàng đã thanh toán.
     */
    @Bean
    public NewTopic orderPaidTopic() {
        return TopicBuilder.name(ORDER_PAID_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Topic ghi nhận lượt click affiliate.
     */
    @Bean
    public NewTopic affiliateClicksTopic() {
        return TopicBuilder.name(AFFILIATE_CLICKS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Topic xử lý sự kiện đơn hàng đã giao thành công.
     */
    @Bean
    public NewTopic orderDeliveredTopic() {
        return TopicBuilder.name(ORDER_DELIVERED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Topic xử lý sự kiện hủy đơn hàng.
     */
    @Bean
    public NewTopic orderCancelledTopic() {
        return TopicBuilder.name(ORDER_CANCELLED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Topic phát tán sự kiện thông báo (notification).
     */
    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name(NOTIFICATION_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

