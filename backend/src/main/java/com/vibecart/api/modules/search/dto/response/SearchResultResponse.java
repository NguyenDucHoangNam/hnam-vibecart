package com.vibecart.api.modules.search.dto.response;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultResponse {
    private List<ProductSearchItemResponse> content;
    private String suggestion;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;
}
