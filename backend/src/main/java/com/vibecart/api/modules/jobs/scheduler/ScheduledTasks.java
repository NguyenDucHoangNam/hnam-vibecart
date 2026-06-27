package com.vibecart.api.modules.jobs.scheduler;

import com.vibecart.api.modules.jobs.entity.BackgroundTask;
import com.vibecart.api.modules.jobs.entity.TaskStatus;
import com.vibecart.api.modules.jobs.repository.BackgroundTaskRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private final JobLauncher jobLauncher;
    private final Job commissionSettlementJob;
    private final BackgroundTaskRepository taskRepository;

    public ScheduledTasks(JobLauncher jobLauncher, Job commissionSettlementJob,
                          BackgroundTaskRepository taskRepository) {
        this.jobLauncher = jobLauncher;
        this.commissionSettlementJob = commissionSettlementJob;
        this.taskRepository = taskRepository;
    }

    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(
            name = "commissionJobLock",
            lockAtMostFor = "PT25M",
            lockAtLeastFor = "PT5M"
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

    @Scheduled(cron = "0 0 3 * * SUN")
    @SchedulerLock(
            name = "taskCleanupLock",
            lockAtMostFor = "PT10M",
            lockAtLeastFor = "PT2M"
    )
    public void cleanupOldTasks() {
        log.info("Starting weekly cleanup of old completed/failed background tasks...");
        try {
            ZonedDateTime cutoff = ZonedDateTime.now().minusDays(30);
            List<BackgroundTask> oldTasks = taskRepository.findByStatusInAndCreatedAtBefore(
                    List.of(TaskStatus.COMPLETED, TaskStatus.FAILED), cutoff);

            if (oldTasks.isEmpty()) {
                log.info("No old background tasks found for cleanup.");
                return;
            }

            taskRepository.deleteAll(oldTasks);
            log.info("Cleaned up {} old background tasks (completed/failed before {})", oldTasks.size(), cutoff);
        } catch (Exception e) {
            log.error("Error during background task cleanup", e);
        }
    }
}
