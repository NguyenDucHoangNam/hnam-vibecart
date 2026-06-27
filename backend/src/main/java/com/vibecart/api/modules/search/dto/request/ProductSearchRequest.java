package com.vibecart.api.modules.search.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchRequest {
    private String query;
    private String q;
    private String categoryId;
    private String category;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String sortBy;
    private String sort;
    private Integer page = 0;
    private Integer size = 12;

    public String getActiveQuery() {
        return (q != null && !q.isBlank()) ? q : query;
    }

    public String getActiveCategoryId() {
        return (categoryId != null && !categoryId.isBlank()) ? categoryId : category;
    }

    public String getActiveSort() {
        String activeSort = (sort != null && !sort.isBlank()) ? sort : sortBy;
        if (activeSort != null) {
            activeSort = switch (activeSort) {
                case "priceAsc" -> "price_asc";
                case "priceDesc" -> "price_desc";
                case "createdAt" -> "newest";
                case "name" -> "relevance";
                default -> activeSort;
            };
        }
        return activeSort != null ? activeSort : "relevance";
    }
}
