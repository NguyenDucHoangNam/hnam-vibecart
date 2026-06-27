package com.vibecart.api.modules.search.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchRequest {
    @Size(max = 200)
    private String query;
    @Size(max = 200)
    private String q;
    private String categoryId;
    private String category;
    @Min(0)
    private BigDecimal minPrice;
    @Min(0)
    private BigDecimal maxPrice;
    private String sortBy;
    private String sort;
    @Min(0)
    private Integer page = 0;
    @Min(1)
    @Max(50)
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
                case "createdAt", "newest" -> "newest";
                default -> activeSort;
            };
        }
        return activeSort != null ? activeSort : "relevance";
    }
}

