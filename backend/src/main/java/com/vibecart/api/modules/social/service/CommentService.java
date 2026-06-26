package com.vibecart.api.modules.social.service;

import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.modules.social.dto.request.CommentRequest;
import com.vibecart.api.modules.social.dto.response.CommentResponse;
public interface CommentService {
    CommentResponse addComment(String postId, CommentRequest request, String username);
    PageResponse<CommentResponse> getComments(String postId, int page, int size);
    void deleteComment(String postId, String commentId, String username, boolean isAdmin);
}
