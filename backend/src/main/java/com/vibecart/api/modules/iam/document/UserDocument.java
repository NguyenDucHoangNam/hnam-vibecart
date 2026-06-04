package com.vibecart.api.modules.iam.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.Set;

@Document(indexName = "users")
@Setting(settingPath = "elasticsearch/settings.json")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDocument {

    @Id
    private String id;

    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "vi_analyzer"),
        otherFields = {
            @InnerField(suffix = "suggest", type = FieldType.Text, analyzer = "autocomplete_analyzer", searchAnalyzer = "vi_analyzer"),
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String username;

    @MultiField(
        mainField = @Field(type = FieldType.Text, analyzer = "vi_analyzer"),
        otherFields = {
            @InnerField(suffix = "suggest", type = FieldType.Text, analyzer = "autocomplete_analyzer", searchAnalyzer = "vi_analyzer"),
            @InnerField(suffix = "keyword", type = FieldType.Keyword)
        }
    )
    private String fullName;

    @Field(type = FieldType.Keyword)
    private String email;

    @Field(type = FieldType.Keyword)
    private String avatarUrl;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private Set<String> roles;

    @Field(type = FieldType.Date)
    private ZonedDateTime createdAt;

    @Field(type = FieldType.Date)
    private ZonedDateTime updatedAt;
}
