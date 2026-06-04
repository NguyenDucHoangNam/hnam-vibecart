package com.vibecart.api.modules.jobs.dto;

import lombok.*;
import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportReportRequest {
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
}
