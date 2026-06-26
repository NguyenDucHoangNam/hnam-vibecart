package com.vibecart.api.modules.jobs.service;

import java.time.ZonedDateTime;
public interface ReportWorkerService {
    void executeExportReportTask(String taskId, String creatorId, ZonedDateTime startDate, ZonedDateTime endDate);
}
