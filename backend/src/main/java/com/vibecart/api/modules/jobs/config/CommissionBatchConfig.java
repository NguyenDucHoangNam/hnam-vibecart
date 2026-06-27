package com.vibecart.api.modules.jobs.config;

import com.vibecart.api.modules.ecommerce.enums.OrderStatus;
import com.vibecart.api.modules.shortener.entity.Commission;
import com.vibecart.api.modules.shortener.entity.CommissionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class CommissionBatchConfig {

    private static final Logger log = LoggerFactory.getLogger(CommissionBatchConfig.class);

    private final DataSource dataSource;

    public CommissionBatchConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Bean
    public Job commissionSettlementJob(JobRepository jobRepository, Step processCommissionsStep) {
        return new JobBuilder("commissionSettlementJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(processCommissionsStep)
                .build();
    }

    @Bean
    public Step processCommissionsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("processCommissionsStep", jobRepository)
                .<Commission, Commission>chunk(1000, transactionManager)
                .reader(commissionReader())
                .processor(commissionProcessor())
                .writer(commissionWriter())
                .faultTolerant()
                .skip(Exception.class)
                .skipLimit(100)
                .build();
    }

    @Bean
    public JdbcPagingItemReader<Commission> commissionReader() {
        SqlPagingQueryProviderFactoryBean queryProvider = new SqlPagingQueryProviderFactoryBean();
        queryProvider.setDataSource(dataSource);
        queryProvider.setSelectClause(
                "SELECT c.id, c.order_id, c.creator_id, c.subtotal_amount, c.commission_rate, " +
                "c.commission_amount, c.status, c.created_at, o.status AS order_status");
        queryProvider.setFromClause("FROM commissions c LEFT JOIN orders o ON c.order_id = o.id");
        queryProvider.setWhereClause(
                "c.status = 'PENDING' AND c.deleted = false AND c.created_at <= CURRENT_DATE - INTERVAL '30 days'");

        Map<String, org.springframework.batch.infrastructure.item.database.Order> sortKeys = new HashMap<>();
        sortKeys.put("c.id", org.springframework.batch.infrastructure.item.database.Order.ASCENDING);
        queryProvider.setSortKeys(sortKeys);

        org.springframework.batch.infrastructure.item.database.PagingQueryProvider provider;
        try {
            provider = queryProvider.getObject();
        } catch (Exception e) {
            log.error("Failed to construct QueryProvider for Spring Batch reader", e);
            throw new IllegalStateException("Batch QueryProvider initialization failure", e);
        }

        JdbcPagingItemReader<Commission> reader = new JdbcPagingItemReader<Commission>(dataSource, provider);
        reader.setPageSize(1000);
        reader.setRowMapper(new CommissionRowMapper());
        return reader;
    }

    @Bean
    public ItemProcessor<Commission, Commission> commissionProcessor() {
        return commission -> {
            String orderStatusStr = commission.getOrderStatusRaw();

            if (orderStatusStr == null) {
                log.warn("Order {} not found for commission {}. Setting as REJECTED",
                        commission.getOrderId(), commission.getId());
                commission.setStatus(CommissionStatus.REJECTED);
                return commission;
            }

            try {
                OrderStatus orderStatus = OrderStatus.valueOf(orderStatusStr);

                if (orderStatus == OrderStatus.DELIVERED) {
                    log.info("Order {} is DELIVERED. Approving commission {}", commission.getOrderId(), commission.getId());
                    commission.setStatus(CommissionStatus.APPROVED);
                    return commission;
                } else if (orderStatus == OrderStatus.CANCELLED) {
                    log.info("Order {} is CANCELLED. Rejecting commission {}", commission.getOrderId(), commission.getId());
                    commission.setStatus(CommissionStatus.REJECTED);
                    return commission;
                }
            } catch (IllegalArgumentException e) {
                log.warn("Unknown order status '{}' for order {}. Skipping commission {}",
                        orderStatusStr, commission.getOrderId(), commission.getId());
            }

            log.debug("Order {} has non-terminal status '{}'. Commission {} remains PENDING",
                    commission.getOrderId(), orderStatusStr, commission.getId());
            return null;
        };
    }

    @Bean
    public ItemWriter<Commission> commissionWriter() {
        JdbcBatchItemWriter<Commission> writer = new JdbcBatchItemWriter<>();
        writer.setDataSource(dataSource);
        writer.setSql("UPDATE commissions SET status = :statusName, updated_at = NOW() WHERE id = :id");
        writer.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>());
        return writer;
    }
}
