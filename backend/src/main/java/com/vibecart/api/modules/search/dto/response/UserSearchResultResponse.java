package com.vibecart.api.modules.search.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchResultResponse {
    private List<UserSearchResponse> content;
    private String suggestion;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;
}
