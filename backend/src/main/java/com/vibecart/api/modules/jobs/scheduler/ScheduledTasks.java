package com.vibecart.api.modules.jobs.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private final JobLauncher jobLauncher;
    private final Job commissionSettlementJob;

    public ScheduledTasks(JobLauncher jobLauncher, Job commissionSettlementJob) {
        this.jobLauncher = jobLauncher;
        this.commissionSettlementJob = commissionSettlementJob;
    }

    // Runs every day at 02:00 AM
    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(
            name = "commissionJobLock",
            lockAtMostFor = "PT25M", // Release lock after 25 minutes max
            lockAtLeastFor = "PT5M"   // Keep lock for at least 5 minutes to prevent multiple runs
    )
    public void runCommissionSettlementJob() {
        log.info("Distributed lock acquired. Initiating Spring Batch Commission Settlement Job...");
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(commissionSettlementJob, params);
            log.info("Spring Batch Commission Settlement Job completed successfully.");
        } catch (Exception e) {
            log.error("Error during Spring Batch Commission Settlement Job execution", e);
        }
    }
}
