package com.vibecart.api.modules.jobs.dto.response;

import lombok.*;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusResponse {
    private String taskId;
    private String taskType;
    private String status;
    private String resultUrl;
    private String errorMessage;
    private ZonedDateTime createdAt;
}
