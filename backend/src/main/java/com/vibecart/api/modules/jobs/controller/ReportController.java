package com.vibecart.api.modules.jobs.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.jobs.dto.request.ExportReportRequest;
import com.vibecart.api.modules.jobs.dto.response.TaskStatusResponse;
import com.vibecart.api.modules.jobs.entity.BackgroundTask;
import com.vibecart.api.modules.jobs.entity.TaskStatus;
import com.vibecart.api.modules.jobs.repository.BackgroundTaskRepository;
import com.vibecart.api.modules.jobs.service.ReportWorkerService;
import com.vibecart.api.common.util.SecurityUtils;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

        private static final Logger log = LoggerFactory.getLogger(ReportController.class);

        private final BackgroundTaskRepository taskRepository;
        private final ReportWorkerService reportWorkerService;

        public ReportController(BackgroundTaskRepository taskRepository,
                        ReportWorkerService reportWorkerService) {
                this.taskRepository = taskRepository;
                this.reportWorkerService = reportWorkerService;
        }

        @PostMapping("/export")
        @PreAuthorize("hasRole('CREATOR')")
        public ResponseEntity<ApiResponse<Map<String, Object>>> exportReport(@Valid @RequestBody ExportReportRequest request) {
                String creatorId = SecurityUtils.getCurrentUserId();
                log.info("REST request to export report for creator: {}", creatorId);

                BackgroundTask task = BackgroundTask.builder()
                                .id(UUID.randomUUID().toString())
                                .userId(creatorId)
                                .taskType("EXCEL_EXPORT")
                                .status(TaskStatus.PENDING)
                                .build();

                task = taskRepository.save(task);

                reportWorkerService.executeExportReportTask(task.getId(), creatorId, request.getStartDate(),
                                request.getEndDate());

                Map<String, Object> result = new HashMap<>();
                result.put("taskId", task.getId());
                result.put("status", "PENDING");
                result.put("message",
                                "Yêu cầu kết xuất báo cáo đã được tiếp nhận thành công. Vui lòng theo dõi trạng thái xử lý.");

                ApiResponse<Map<String, Object>> response = ApiResponse.<Map<String, Object>>builder()
                                .code(1000)
                                .message("Tiếp nhận yêu cầu xuất báo cáo thành công")
                                .result(result)
                                .build();

                return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        }

        @GetMapping("/tasks/{taskId}")
        @PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")
        public ResponseEntity<ApiResponse<TaskStatusResponse>> getTaskStatus(@PathVariable String taskId) {
                String currentUserId = SecurityUtils.getCurrentUserId();
                log.info("REST request to query status for TaskID={}. Requested by: {}", taskId, currentUserId);

                BackgroundTask task = taskRepository.findById(taskId)
                                .orElseThrow(() -> new AppException(ErrorCode.INVALID_INPUT));

                boolean isAdmin = SecurityUtils.isAdmin();

                if (!task.getUserId().equals(currentUserId) && !isAdmin) {
                        log.warn("Unauthorized access attempt to task {} by user {}", taskId, currentUserId);
                        throw new AppException(ErrorCode.UNAUTHORIZED);
                }

                ZonedDateTime createdAtZoned = task.getCreatedAt();

                TaskStatusResponse result = TaskStatusResponse.builder()
                                .taskId(task.getId())
                                .taskType(task.getTaskType())
                                .status(task.getStatus().name())
                                .resultUrl(task.getResultUrl())
                                .errorMessage(task.getErrorMessage())
                                .createdAt(createdAtZoned)
                                .build();

                ApiResponse<TaskStatusResponse> response = ApiResponse.<TaskStatusResponse>builder()
                                .code(1000)
                                .message("Truy vấn trạng thái tiến trình thành công")
                                .result(result)
                                .build();

                return ResponseEntity.ok(response);
        }
}
