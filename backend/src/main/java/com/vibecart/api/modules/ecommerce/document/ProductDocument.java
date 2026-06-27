package com.vibecart.api.modules.ecommerce.document;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Document(indexName = "products")
@Setting(settingPath = "elasticsearch/settings.json")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {

    @Id
    private String id;

    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "vi_analyzer"),
        otherFields = {
            @InnerField(suffix = "suggest", type = FieldType.Text, analyzer = "autocomplete_analyzer", searchAnalyzer = "vi_analyzer"),
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String name;

    @Field(type = FieldType.Text, analyzer = "vi_analyzer")
    private String description;

    @Field(type = FieldType.Keyword)
    private String categoryId;

    @Field(type = FieldType.Keyword)
    private String categoryName;

    @Field(type = FieldType.Keyword)
    private String creatorId;

    @Field(type = FieldType.Keyword)
    private String thumbnailUrl;

    @Field(type = FieldType.Double)
    private BigDecimal minPrice;

    @Field(type = FieldType.Double)
    private BigDecimal maxPrice;

    @Field(type = FieldType.Double)
    private BigDecimal minOriginalPrice;

    @Field(type = FieldType.Double)
    private BigDecimal maxOriginalPrice;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date)
    private ZonedDateTime createdAt;

    @Field(type = FieldType.Date)
    private ZonedDateTime updatedAt;
}
