package com.vibecart.api.modules.social.mapper;

import com.vibecart.api.modules.social.dto.response.CommentResponse;
import com.vibecart.api.modules.social.entity.PostComment;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CommentMapper {

    @Mapping(target = "postId", source = "comment.post.id")
    @Mapping(target = "userId", source = "comment.user.id")
    @Mapping(target = "username", source = "comment.user.username")
    @Mapping(target = "userAvatarUrl", source = "comment.user.avatarUrl")
    @Mapping(target = "parentId", source = "comment.parent.id")
    @Mapping(target = "replies", source = "replies")
    @Mapping(target = "id", source = "comment.id")
    @Mapping(target = "content", source = "comment.content")
    @Mapping(target = "createdAt", source = "comment.createdAt")
    @Mapping(target = "updatedAt", source = "comment.updatedAt")
    CommentResponse toCommentResponse(PostComment comment, List<CommentResponse> replies);
}
