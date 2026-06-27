package com.vibecart.api.modules.jobs.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportReportRequest {
    @NotNull
    private ZonedDateTime startDate;
    @NotNull
    private ZonedDateTime endDate;
}
