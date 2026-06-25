package com.vibecart.api.modules.jobs.service;

import java.time.ZonedDateTime;

/**
 * Service xuất báo cáo Excel bất đồng bộ và upload lên S3.
 */
public interface ReportWorkerService {
    void executeExportReportTask(String taskId, String creatorId, ZonedDateTime startDate, ZonedDateTime endDate);
}
