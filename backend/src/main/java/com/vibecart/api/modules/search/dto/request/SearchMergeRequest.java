package com.vibecart.api.modules.search.dto.request;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchMergeRequest {
    private List<SearchMergeItem> keywords;
}
