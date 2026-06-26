package com.vibecart.api.modules.ecommerce.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class OrderTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScheduler.class);

    private final OrderService orderService;

    public OrderTimeoutScheduler(OrderService orderService) {
        this.orderService = orderService;
    }
    @Scheduled(cron = "0 */1 * * * *")
    public void cancelExpiredOrders() {
        log.debug("Running order timeout check...");
        try {
            orderService.cancelExpiredOrders();
        } catch (Exception e) {
            log.error("Error during order timeout check: ", e);
        }
    }
}
