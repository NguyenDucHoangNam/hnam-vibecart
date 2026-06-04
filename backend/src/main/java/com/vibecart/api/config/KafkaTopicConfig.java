package com.vibecart.api.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
public class KafkaTopicConfig {

    public static final String PRODUCT_SYNC_TOPIC = "product-sync-topic";
    public static final String ORDER_PAID_TOPIC = "order-paid-topic";
    public static final String AFFILIATE_CLICKS_TOPIC = "affiliate-clicks-topic";
    public static final String ORDER_DELIVERED_TOPIC = "order-delivered-topic";
    public static final String ORDER_CANCELLED_TOPIC = "order-cancelled-topic";
    public static final String NOTIFICATION_EVENTS_TOPIC = "notification-events";

    @Bean
    public NewTopic productSyncTopic() {
        return TopicBuilder.name(PRODUCT_SYNC_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderPaidTopic() {
        return TopicBuilder.name(ORDER_PAID_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic affiliateClicksTopic() {
        return TopicBuilder.name(AFFILIATE_CLICKS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderDeliveredTopic() {
        return TopicBuilder.name(ORDER_DELIVERED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return TopicBuilder.name(ORDER_CANCELLED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name(NOTIFICATION_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

