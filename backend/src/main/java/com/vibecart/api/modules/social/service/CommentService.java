package com.vibecart.api.modules.social.service;

import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.modules.social.dto.request.CommentRequest;
import com.vibecart.api.modules.social.dto.response.CommentResponse;

public interface CommentService {

    /** Thêm comment (hoặc reply) vào bài viết */
    CommentResponse addComment(String postId, CommentRequest request, String username);

    /** Lấy danh sách comments (root + replies) phân trang */
    PageResponse<CommentResponse> getComments(String postId, int page, int size);

    /** Xóa comment (owner, post creator, hoặc ADMIN) */
    void deleteComment(String postId, String commentId, String username, boolean isAdmin);
}
