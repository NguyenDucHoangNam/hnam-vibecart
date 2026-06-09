package com.vibecart.api.modules.jobs.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller cho Admin kích hoạt chạy thủ công các Batch Job.
 */
@RestController
@RequestMapping("/api/v1/admin/jobs")
public class AdminJobController {

    private static final Logger log = LoggerFactory.getLogger(AdminJobController.class);

    private final JobLauncher jobLauncher;
    private final Job commissionSettlementJob;

    public AdminJobController(JobLauncher jobLauncher, Job commissionSettlementJob) {
        this.jobLauncher = jobLauncher;
        this.commissionSettlementJob = commissionSettlementJob;
    }

    @PostMapping("/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> triggerJob(@RequestParam String jobName) {
        log.info("Admin manually triggering Spring Batch job: {}", jobName);

        if (!"commissionSettlementJob".equals(jobName)) {
            log.warn("Attempted to trigger unrecognized batch job: {}", jobName);
            throw new AppException(ErrorCode.INVALID_INPUT); // Unknown job name
        }

        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .addString("triggeredBy", "ADMIN_REST_API")
                    .toJobParameters();

            jobLauncher.run(commissionSettlementJob, params);
            log.info("Manually triggered commissionSettlementJob completed successfully.");

            ApiResponse<Void> response = ApiResponse.<Void>builder()
                    .code(1000)
                    .message("Batch Job [commissionSettlementJob] đã được kích hoạt chạy thủ công cưỡng bức thành công")
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to execute manual batch job trigger", e);
            throw new RuntimeException("Lỗi kích hoạt Batch Job thủ công: " + e.getMessage(), e);
        }
    }
}
