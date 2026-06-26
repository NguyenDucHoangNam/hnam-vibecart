package com.vibecart.api.modules.search.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "search_histories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistory {

    @Id
    private String id;

    @Builder.Default
    private List<SearchItem> items = new ArrayList<>();

    private LocalDateTime updatedAt;
}
