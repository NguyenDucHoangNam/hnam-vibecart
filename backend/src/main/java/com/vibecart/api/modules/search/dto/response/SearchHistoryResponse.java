package com.vibecart.api.modules.search.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistoryResponse {
    private String keyword;
    private LocalDateTime searchedAt;
}
