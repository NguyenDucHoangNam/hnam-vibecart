package com.vibecart.api.modules.social.mapper;

import com.vibecart.api.modules.ecommerce.entity.Product;
import com.vibecart.api.modules.social.dto.response.PostResponse;
import com.vibecart.api.modules.social.entity.Post;
import org.mapstruct.*;

import java.util.*;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface PostMapper {

    @Mapping(target = "creatorId", source = "post.creator.id")
    @Mapping(target = "creatorUsername", source = "post.creator.username")
    @Mapping(target = "creatorFullName", source = "post.creator.fullName")
    @Mapping(target = "creatorAvatarUrl", source = "post.creator.avatarUrl")
    @Mapping(target = "mediaUrls", expression = "java(splitMediaUrls(post.getMediaUrls()))")
    @Mapping(target = "taggedProductIds", source = "post.taggedProducts")
    @Mapping(target = "likeCount", source = "likeCount")
    @Mapping(target = "commentCount", source = "commentCount")
    @Mapping(target = "likedByMe", source = "likedByMe")
    @Mapping(target = "createdAt", source = "post.createdAt")
    @Mapping(target = "updatedAt", source = "post.updatedAt")
    @Mapping(target = "id", source = "post.id")
    @Mapping(target = "content", source = "post.content")
    PostResponse toPostResponse(Post post, long likeCount, long commentCount, boolean likedByMe);

    default List<String> splitMediaUrls(String mediaUrls) {
        if (mediaUrls == null || mediaUrls.isBlank()) return List.of();
        return Arrays.stream(mediaUrls.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    default String mapProduct(Product product) {
        return product != null ? product.getId() : null;
    }
}
