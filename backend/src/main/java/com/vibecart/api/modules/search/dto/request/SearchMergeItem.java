package com.vibecart.api.modules.search.dto.request;

import lombok.*;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchMergeItem {
    private String keyword;
    private Instant searchedAt;
}
