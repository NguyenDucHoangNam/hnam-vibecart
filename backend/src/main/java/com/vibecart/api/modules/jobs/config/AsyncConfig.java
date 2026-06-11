package com.vibecart.api.modules.jobs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Cấu hình Async thread pool cho các background task.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "reportTaskExecutor")
    public Executor reportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ReportWorker-");
        executor.initialize();
        return executor;
    }

    /**
     * Thread pool riêng cho Fan-out on Write (News Feed).
     * Core=4 để xử lý đồng thời nhiều fan-out, max=16 cho burst khi creator có nhiều follower.
     * Queue=200 để buffer các tác vụ khi thread pool đầy.
     */
    @Bean(name = "feedFanoutExecutor")
    public Executor feedFanoutExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("FeedFanout-");
        executor.initialize();
        return executor;
    }
}
