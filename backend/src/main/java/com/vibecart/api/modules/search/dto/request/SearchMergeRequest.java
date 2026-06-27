package com.vibecart.api.modules.search.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchMergeRequest {
    @Size(max = 50)
    private List<SearchMergeItem> keywords;
}

