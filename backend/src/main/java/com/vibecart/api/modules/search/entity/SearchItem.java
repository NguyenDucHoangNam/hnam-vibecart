package com.vibecart.api.modules.search.entity;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchItem {
    private String keyword;
    private LocalDateTime searchedAt;
}
