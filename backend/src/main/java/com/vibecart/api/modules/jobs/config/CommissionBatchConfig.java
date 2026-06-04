package com.vibecart.api.modules.jobs.config;

import com.vibecart.api.modules.ecommerce.entity.Order;
import com.vibecart.api.modules.ecommerce.enums.OrderStatus;
import com.vibecart.api.modules.ecommerce.repository.OrderRepository;
import com.vibecart.api.modules.shortener.entity.Commission;
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
import java.util.Optional;

@Configuration
public class CommissionBatchConfig {

    private static final Logger log = LoggerFactory.getLogger(CommissionBatchConfig.class);

    private final DataSource dataSource;
    private final OrderRepository orderRepository;

    public CommissionBatchConfig(DataSource dataSource, OrderRepository orderRepository) {
        this.dataSource = dataSource;
        this.orderRepository = orderRepository;
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
        queryProvider.setSelectClause("SELECT id, order_id, creator_id, subtotal_amount, commission_rate, commission_amount, status, created_at");
        queryProvider.setFromClause("FROM commissions");
        // Filter pending commission orders older than 30 days
        queryProvider.setWhereClause("status = 'PENDING' AND created_at <= CURRENT_DATE - INTERVAL '30 days'");
        Map<String, org.springframework.batch.infrastructure.item.database.Order> sortKeys = new HashMap<>();
        sortKeys.put("id", org.springframework.batch.infrastructure.item.database.Order.ASCENDING);
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
            Optional<Order> orderOpt = orderRepository.findById(commission.getOrderId());
            if (orderOpt.isEmpty()) {
                log.warn("Order {} not found in database. Setting commission {} as REJECTED", commission.getOrderId(), commission.getId());
                commission.setStatus("REJECTED");
                return commission;
            }

            Order order = orderOpt.get();
            if (order.getStatus() == OrderStatus.DELIVERED) {
                log.info("Order {} is DELIVERED. Approving commission {}", order.getId(), commission.getId());
                commission.setStatus("APPROVED");
                return commission;
            } else if (order.getStatus() == OrderStatus.CANCELLED) {
                log.info("Order {} is CANCELLED. Rejecting commission {}", order.getId(), commission.getId());
                commission.setStatus("REJECTED");
                return commission;
            }

            // If order is still pending, paid, shipped, etc. we do not finalize commission yet.
            // Returning null filters this commission out of this chunk execution, keeping it PENDING.
            return null;
        };
    }

    @Bean
    public ItemWriter<Commission> commissionWriter() {
        JdbcBatchItemWriter<Commission> writer = new JdbcBatchItemWriter<>();
        writer.setDataSource(dataSource);
        writer.setSql("UPDATE commissions SET status = :status, updated_at = NOW() WHERE id = :id");
        writer.setItemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>());
        return writer;
    }
}
