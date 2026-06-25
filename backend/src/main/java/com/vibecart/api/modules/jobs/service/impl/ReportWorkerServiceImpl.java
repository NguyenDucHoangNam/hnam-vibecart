package com.vibecart.api.modules.jobs.service.impl;

import com.vibecart.api.common.service.StorageService;
import com.vibecart.api.modules.ecommerce.entity.Order;
import com.vibecart.api.modules.ecommerce.repository.OrderRepository;
import com.vibecart.api.modules.jobs.entity.BackgroundTask;
import com.vibecart.api.modules.jobs.entity.TaskStatus;
import com.vibecart.api.modules.jobs.repository.BackgroundTaskRepository;
import com.vibecart.api.modules.jobs.service.ReportWorkerService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Implementation của {@link ReportWorkerService} xuất báo cáo Excel bất đồng bộ và upload lên S3.
 */
@Service
public class ReportWorkerServiceImpl implements ReportWorkerService {

    private static final Logger log = LoggerFactory.getLogger(ReportWorkerServiceImpl.class);

    private final BackgroundTaskRepository taskRepository;
    private final OrderRepository orderRepository;
    private final StorageService storageService;

    public ReportWorkerServiceImpl(BackgroundTaskRepository taskRepository,
                                   OrderRepository orderRepository,
                                   StorageService storageService) {
        this.taskRepository = taskRepository;
        this.orderRepository = orderRepository;
        this.storageService = storageService;
    }

    @Override
    @Async("reportTaskExecutor")
    public void executeExportReportTask(String taskId, String creatorId, ZonedDateTime startDate, ZonedDateTime endDate) {
        log.info("Starting async report export worker. TaskID={}, CreatorID={}", taskId, creatorId);

        updateTaskStatus(taskId, TaskStatus.RUNNING, null, null);

        try {
            List<Order> orders = orderRepository.findByCreatorIdWithItems(creatorId);

            if (startDate != null) {
                orders = orders.stream().filter(o -> o.getCreatedAt().isAfter(startDate)).toList();
            }
            if (endDate != null) {
                orders = orders.stream().filter(o -> o.getCreatedAt().isBefore(endDate)).toList();
            }

            ByteArrayOutputStream outputStream = generateExcelReport(orders);

            String s3Key = "reports/" + creatorId + "/" + UUID.randomUUID().toString() + ".xlsx";
            byte[] bytes = outputStream.toByteArray();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);

            log.info("Uploading rendered Excel file to S3 storage key: {}", s3Key);
            String downloadUrl = storageService.uploadFile(
                    s3Key,
                    inputStream,
                    bytes.length,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            );

            updateTaskStatus(taskId, TaskStatus.COMPLETED, downloadUrl, null);
            log.info("Successfully completed report export task. TaskID={}, URL={}", taskId, downloadUrl);
        } catch (Exception e) {
            log.error("Failed to execute async report export for TaskID={}", taskId, e);
            updateTaskStatus(taskId, TaskStatus.FAILED, null, e.getMessage());
        }
    }

    private ByteArrayOutputStream generateExcelReport(List<Order> orders) throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Creator Sales Report");

            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Mã Đơn Hàng", "Tên Người Nhận", "Số Điện Thoại", "Địa Chỉ Giao Hàng",
                    "Trạng Thái", "Tổng Tiền", "Giảm Giá", "Thanh Toán Thực Tế", "Thời Gian Tạo"
            };

            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowNum = 1;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            for (Order order : orders) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(order.getOrderCode());
                row.createCell(1).setCellValue(order.getRecipientName());
                row.createCell(2).setCellValue(order.getRecipientPhone());
                row.createCell(3).setCellValue(order.getShippingAddress());
                row.createCell(4).setCellValue(order.getStatus().name());
                row.createCell(5).setCellValue(order.getTotalAmount() != null ? order.getTotalAmount().doubleValue() : 0.0);
                row.createCell(6).setCellValue(order.getDiscountAmount() != null ? order.getDiscountAmount().doubleValue() : 0.0);
                row.createCell(7).setCellValue(order.getFinalAmount() != null ? order.getFinalAmount().doubleValue() : 0.0);

                String formattedDate = order.getCreatedAt() != null ? order.getCreatedAt().format(formatter) : "";
                row.createCell(8).setCellValue(formattedDate);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(bos);
            return bos;
        }
    }

    private void updateTaskStatus(String taskId, TaskStatus status, String url, String error) {
        try {
            BackgroundTask task = taskRepository.findById(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
            task.setStatus(status);
            task.setResultUrl(url);
            task.setErrorMessage(error);
            task.setUpdatedAt(ZonedDateTime.now());
            taskRepository.save(task);
        } catch (Exception e) {
            log.error("Failed to update status for background task: {}", taskId, e);
        }
    }
}
