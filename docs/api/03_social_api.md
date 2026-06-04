# 🔌 Đặc tả API - Phân hệ 3: Mạng xã hội thu nhỏ (Social Mini-Network)

Tài liệu này đặc tả chi tiết hợp đồng tích hợp API RESTful cho các hoạt động đăng tải Bài viết (Posts), Quản lý theo dõi người dùng (Follows), Tương tác thích bài viết (Likes) và Bình luận lồng cấp đệ quy (Comments) của hệ thống **VibeCart**.

---

## 📝 1. Các Endpoints Bài viết (Post APIs)

Các hoạt động quản lý bài viết nội dung đính kèm hình ảnh/video và gắn thẻ sản phẩm (Product Tagging).

### 1.1 Đăng bài viết mới (Create Post)
*   **Method:** `POST`
*   **Path:** `/api/v1/posts`
*   **Auth Level:** `Require ROLE_CREATOR` (Token Bearer)
*   **Validation:** Media URLs phải tồn tại trong hệ thống và thuộc quyền sở hữu của Creator hiện tại.
*   **Error Responses:** `MEDIA_NOT_FOUND (4004)`, `MEDIA_NOT_VERIFIED (4005)`, `MEDIA_ACCESS_DENIED (4006)`, `MAX_MEDIA_EXCEEDED (3006)`, `MAX_PRODUCTS_EXCEEDED (3007)`
*   **Request Body:**
    ```json
    {
      "content": "Đây là bài viết giới thiệu tai nghe đỉnh cao từ Sony, click để mua ngay nhận voucher giảm 20% nhé các bạn!",
      "mediaUrls": [
        "https://vibecart.com/images/post1.jpg",
        "https://vibecart.com/images/post1_sub.jpg"
      ],
      "taggedProductIds": [
        "c8c4df5b-789a-4def-0123-456789abcdef"
      ]
    }
    ```
*   **Response Success (201 Created):**
    ```json
    {
      "code": 1000,
      "message": "Tạo bài viết thành công",
      "result": {
        "id": "e987f65a-1234-5678-abcd-ef0123456789",
        "creatorId": "a1b2c3d4-5678-90ab-cdef-1234567890ab",
        "creatorUsername": "creator_vibe",
        "creatorFullName": "Creator VibeCart",
        "creatorAvatarUrl": "https://vibecart.com/avatar.jpg",
        "content": "Đây là bài viết giới thiệu tai nghe đỉnh cao từ Sony, click để mua ngay nhận voucher giảm 20% nhé các bạn!",
        "mediaUrls": [
          "https://vibecart.com/images/post1.jpg",
          "https://vibecart.com/images/post1_sub.jpg"
        ],
        "taggedProductIds": [
          "c8c4df5b-789a-4def-0123-456789abcdef"
        ],
        "likeCount": 0,
        "commentCount": 0,
        "likedByMe": false,
        "createdAt": "2026-06-02T23:45:00+07:00",
        "updatedAt": "2026-06-02T23:45:00+07:00"
      }
    }
    ```

---

### 1.2 Xem chi tiết bài viết (Get Post Details)
*   **Method:** `GET`
*   **Path:** `/api/v1/posts/{postId}`
*   **Auth Level:** `PermitAll` (Nhưng nếu có Bearer Token sẽ điền trạng thái `likedByMe` tương ứng)
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy chi tiết bài viết thành công",
      "result": {
        "id": "e987f65a-1234-5678-abcd-ef0123456789",
        "creatorId": "a1b2c3d4-5678-90ab-cdef-1234567890ab",
        "creatorUsername": "creator_vibe",
        "creatorFullName": "Creator VibeCart",
        "creatorAvatarUrl": "https://vibecart.com/avatar.jpg",
        "content": "Đây là bài viết giới thiệu tai nghe đỉnh cao từ Sony, click để mua ngay nhận voucher giảm 20% nhé các bạn!",
        "mediaUrls": [
          "https://vibecart.com/images/post1.jpg"
        ],
        "taggedProductIds": [
          "c8c4df5b-789a-4def-0123-456789abcdef"
        ],
        "likeCount": 150,
        "commentCount": 42,
        "likedByMe": true,
        "createdAt": "2026-06-02T23:45:00+07:00",
        "updatedAt": "2026-06-02T23:50:00+07:00"
      }
    }
    ```

---

### 1.3 Cập nhật bài viết (Update Post)
*   **Method:** `PUT`
*   **Path:** `/api/v1/posts/{postId}`
*   **Auth Level:** `Require ROLE_CREATOR` (Chỉ chính chủ Creator đăng bài được phép cập nhật)
*   **Error Responses:** `POST_ACCESS_DENIED (3002)` nếu không phải chủ bài viết, `MEDIA_NOT_FOUND (4004)`, `MEDIA_NOT_VERIFIED (4005)`, `MEDIA_ACCESS_DENIED (4006)`
*   **Request Body:**
    ```json
    {
      "content": "Nội dung bài viết mới cập nhật, chất lượng đỉnh cao!",
      "mediaUrls": [
        "https://vibecart.com/images/post1_updated.jpg"
      ],
      "taggedProductIds": [
        "c8c4df5b-789a-4def-0123-456789abcdef"
      ]
    }
    ```
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Cập nhật bài viết thành công",
      "result": {
        "id": "e987f65a-1234-5678-abcd-ef0123456789",
        "creatorId": "a1b2c3d4-5678-90ab-cdef-1234567890ab",
        "creatorUsername": "creator_vibe",
        "creatorFullName": "Creator VibeCart",
        "creatorAvatarUrl": "https://vibecart.com/avatar.jpg",
        "content": "Nội dung bài viết mới cập nhật, chất lượng đỉnh cao!",
        "mediaUrls": [
          "https://vibecart.com/images/post1_updated.jpg"
        ],
        "taggedProductIds": [
          "c8c4df5b-789a-4def-0123-456789abcdef"
        ],
        "likeCount": 150,
        "commentCount": 42,
        "likedByMe": true,
        "createdAt": "2026-06-02T23:45:00+07:00",
        "updatedAt": "2026-06-02T23:55:00+07:00"
      }
    }
    ```

---

### 1.4 Xóa bài viết (Delete Post)
*   **Method:** `DELETE`
*   **Path:** `/api/v1/posts/{postId}`
*   **Auth Level:** `Require Bearer Token` (Chỉ chính chủ Creator tạo ra hoặc tài khoản ADMIN được phép xóa)
*   **Error Responses:** `POST_ACCESS_DENIED (3002)` nếu không phải chủ bài viết và không phải Admin
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Đã xóa bài viết thành công",
      "result": null
    }
    ```

---

## 📱 2. API Bảng tin (News Feed API)

Tải danh sách bài viết tổng hợp từ những Creator mà Shopper đang theo dõi cùng với bài viết của chính người mua, sắp xếp theo thời gian mới nhất.

> **⚡ Lưu ý phân trang:** API này trả về `totalElements = -1` và `totalPages = -1`. Client nên dùng trường `last` để xác định có trang tiếp theo hay không (Infinite Scroll Pattern).

*   **Method:** `GET`
*   **Path:** `/api/v1/posts/feed`
*   **Auth Level:** `Require Bearer Token`
*   **Query Parameters:**
    *   `page`: Số trang cần lấy (mặc định: `0`)
    *   `size`: Số lượng bài viết mỗi trang (mặc định: `20`)
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy news feed thành công",
      "result": {
        "content": [
          {
            "id": "e987f65a-1234-5678-abcd-ef0123456789",
            "creatorId": "a1b2c3d4-5678-90ab-cdef-1234567890ab",
            "creatorUsername": "creator_vibe",
            "creatorFullName": "Creator VibeCart",
            "creatorAvatarUrl": "https://vibecart.com/avatar.jpg",
            "content": "Đây là bài viết giới thiệu tai nghe đỉnh cao từ Sony, click để mua ngay nhận voucher giảm 20% nhé các bạn!",
            "mediaUrls": [
              "https://vibecart.com/images/post1.jpg"
            ],
            "taggedProductIds": [
              "c8c4df5b-789a-4def-0123-456789abcdef"
            ],
            "likeCount": 150,
            "commentCount": 42,
            "likedByMe": true,
            "createdAt": "2026-06-02T23:45:00+07:00",
            "updatedAt": "2026-06-02T23:50:00+07:00"
          }
        ],
        "page": 0,
        "size": 20,
        "totalElements": -1,
        "totalPages": -1,
        "last": false
      }
    }
    ```

---

## 💬 3. Các Endpoints Tương tác (Likes & Comments APIs)

### 3.1 Thích/Bỏ thích bài viết (Like/Unlike Toggle)
*   **Method:** `POST`
*   **Path:** `/api/v1/posts/{postId}/likes`
*   **Auth Level:** `Require Bearer Token`
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Đã thích bài viết", // hoặc "Đã bỏ thích bài viết"
      "result": true // true là đã Like thành công, false là đã Unlike thành công
    }
    ```

---

### 3.2 Kiểm tra trạng thái thích bài viết (Check Liked Status)
*   **Method:** `GET`
*   **Path:** `/api/v1/posts/{postId}/likes/check`
*   **Auth Level:** `Require Bearer Token`
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy dữ liệu thành công",
      "result": true // true nếu user đã thích bài viết này, ngược lại false
    }
    ```

---

### 3.3 Lấy tổng số lượt thích (Get Like Count)
*   **Method:** `GET`
*   **Path:** `/api/v1/posts/{postId}/likes/count`
*   **Auth Level:** `PermitAll`
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy dữ liệu thành công",
      "result": 150
    }
    ```

---

### 3.4 Viết bình luận / Phản hồi bình luận (Add Comment)
*   **Method:** `POST`
*   **Path:** `/api/v1/posts/{postId}/comments`
*   **Auth Level:** `Require Bearer Token` (Trước khi lưu sẽ được kiểm duyệt tự động bởi Profanity Filter)
*   **Request Body:**
    ```json
    {
      "parentId": "d741c88a-bcde-0123-4567-89abcdef0123", // Nhận UUID bình luận cha nếu là phản hồi, hoặc null nếu là bình luận cấp 1
      "content": "Tai nghe này dùng êm lắm nha mọi người, bass cực căng!"
    }
    ```
*   **Response Success (201 Created):**
    ```json
    {
      "code": 1000,
      "message": "Thêm bình luận thành công",
      "result": {
        "id": "f852d99b-cdef-1234-5678-90abcdef1234",
        "postId": "e987f65a-1234-5678-abcd-ef0123456789",
        "userId": "9999a888-7777-6666-5555-444433332222",
        "username": "shopper_vip",
        "userAvatarUrl": "https://vibecart.com/shoppers/avatar.jpg",
        "content": "Tai nghe này dùng êm lắm nha mọi người, bass cực căng!",
        "parentId": "d741c88a-bcde-0123-4567-89abcdef0123",
        "replies": [],
        "createdAt": "2026-06-02T23:59:00+07:00",
        "updatedAt": "2026-06-02T23:59:00+07:00"
      }
    }
    ```

---

### 3.5 Tải cây bình luận bài viết (Get Nested Comments)
Lấy danh sách các bình luận có cấu trúc hình cây lồng ghép sâu tối đa 3 cấp, được sắp xếp hoàn chỉnh.
*   **Method:** `GET`
*   **Path:** `/api/v1/posts/{postId}/comments`
*   **Auth Level:** `PermitAll`
*   **Query Parameters:**
    *   `page`: Số trang bình luận gốc (mặc định: `0`)
    *   `size`: Số lượng bình luận gốc mỗi trang (mặc định: `20`)
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy danh sách bình luận thành công",
      "result": {
        "content": [
          {
            "id": "d741c88a-bcde-0123-4567-89abcdef0123",
            "postId": "e987f65a-1234-5678-abcd-ef0123456789",
            "userId": "8888b777-6666-5555-4444-333322221111",
            "username": "shopper_normal",
            "userAvatarUrl": "https://vibecart.com/shoppers/avatar2.jpg",
            "content": "Sản phẩm chất lượng thế nào shop?",
            "parentId": null,
            "replies": [
              {
                "id": "f852d99b-cdef-1234-5678-90abcdef1234",
                "postId": "e987f65a-1234-5678-abcd-ef0123456789",
                "userId": "9999a888-7777-6666-5555-444433332222",
                "username": "shopper_vip",
                "userAvatarUrl": "https://vibecart.com/shoppers/avatar.jpg",
                "content": "Tai nghe này dùng êm lắm nha mọi người, bass cực căng!",
                "parentId": "d741c88a-bcde-0123-4567-89abcdef0123",
                "replies": [],
                "createdAt": "2026-06-02T23:59:00+07:00",
                "updatedAt": "2026-06-02T23:59:00+07:00"
              }
            ],
            "createdAt": "2026-06-02T23:56:00+07:00",
            "updatedAt": "2026-06-02T23:56:00+07:00"
          }
        ],
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1,
        "last": true
      }
    }
    ```

---

### 3.6 Xóa bình luận (Delete Comment)
*   **Method:** `DELETE`
*   **Path:** `/api/v1/posts/{postId}/comments/{commentId}`
*   **Auth Level:** `Require Bearer Token` (Chỉ chính chủ viết bình luận, Creator chủ bài viết, hoặc ADMIN được phép xóa)
*   **Error Responses:** `COMMENT_ACCESS_DENIED (3004)` nếu không có quyền xóa
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Đã xóa bình luận thành công",
      "result": null
    }
    ```

---

## 🤝 4. Các Endpoints Theo dõi & Hồ sơ (Follow & Profile APIs)

### 4.1 Theo dõi / Hủy theo dõi người dùng (Follow Toggle)
Cho phép Shopper theo dõi hoặc hủy theo dõi một User khác thông qua ID.
*   **Method:** `POST`
*   **Path:** `/api/v1/users/{userId}/follow`
*   **Auth Level:** `Require Bearer Token` (Không thể tự theo dõi chính mình)
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Đã theo dõi thành công", // hoặc "Đã hủy theo dõi"
      "result": true // true là đã Follow, false là đã Unfollow
    }
    ```

---

### 4.2 Lấy danh sách đang theo dõi (Get Following List)
Lấy danh sách các Creator/User mà người dùng chỉ định đang theo dõi.
*   **Method:** `GET`
*   **Path:** `/api/v1/users/{userId}/following`
*   **Auth Level:** `PermitAll` (Nhưng điền trạng thái `followedByMe` tương ứng nếu có Bearer Token)
*   **Query Parameters:**
    *   `page`: Số trang (mặc định: `0`)
    *   `size`: Số lượng mỗi trang (mặc định: `20`)
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy danh sách đang theo dõi thành công",
      "result": {
        "content": [
          {
            "userId": "a1b2c3d4-5678-90ab-cdef-1234567890ab",
            "username": "creator_vibe",
            "fullName": "Creator VibeCart",
            "avatarUrl": "https://vibecart.com/avatar.jpg",
            "followedByMe": true,
            "followedAt": "2026-06-01T15:30:00+07:00"
          }
        ],
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1,
        "last": true
      }
    }
    ```

---

### 4.3 Lấy danh sách người theo dõi mình (Get Followers List)
Lấy danh sách các User đang theo dõi người dùng chỉ định.
*   **Method:** `GET`
*   **Path:** `/api/v1/users/{userId}/followers`
*   **Auth Level:** `PermitAll` (Nhưng điền trạng thái `followedByMe` tương ứng nếu có Bearer Token)
*   **Query Parameters:**
    *   `page`: Số trang (mặc định: `0`)
    *   `size`: Số lượng mỗi trang (mặc định: `20`)
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy danh sách người theo dõi thành công",
      "result": {
        "content": [
          {
            "userId": "9999a888-7777-6666-5555-444433332222",
            "username": "shopper_vip",
            "fullName": "Shopper VIP VibeCart",
            "avatarUrl": "https://vibecart.com/shoppers/avatar.jpg",
            "followedByMe": false,
            "followedAt": "2026-06-02T10:00:00+07:00"
          }
        ],
        "page": 0,
        "size": 20,
        "totalElements": 1,
        "totalPages": 1,
        "last": true
      }
    }
    ```

---

### 4.4 Kiểm tra trạng thái theo dõi (Check Follow Status)
Kiểm tra xem User đăng nhập hiện tại đã follow một User mục tiêu hay chưa.
*   **Method:** `GET`
*   **Path:** `/api/v1/users/{userId}/follow/check`
*   **Auth Level:** `Require Bearer Token`
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy dữ liệu thành công",
      "result": true // true nếu đã follow, ngược lại false
    }
    ```

---

### 4.5 Lấy tổng số người theo dõi (Get Followers Count)
*   **Method:** `GET`
*   **Path:** `/api/v1/users/{userId}/followers/count`
*   **Auth Level:** `PermitAll`
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy dữ liệu thành công",
      "result": 12500
    }
    ```

---

### 4.6 Lấy tổng số người đang theo dõi (Get Following Count)
*   **Method:** `GET`
*   **Path:** `/api/v1/users/{userId}/following/count`
*   **Auth Level:** `PermitAll`
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy dữ liệu thành công",
      "result": 82
    }
    ```

---

### 4.7 Lấy thông tin trang cá nhân User (Get User Profile)
Lấy thông tin Profile cơ bản và phân quyền của User chỉ định.
*   **Method:** `GET`
*   **Path:** `/api/v1/users/{userId}/profile`
*   **Auth Level:** `PermitAll`
*   **Response Success (200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy thông tin cá nhân thành công",
      "result": {
        "id": "a1b2c3d4-5678-90ab-cdef-1234567890ab",
        "username": "creator_vibe",
        "email": "creator@vibecart.com",
        "fullName": "Creator VibeCart",
        "avatarUrl": "https://vibecart.com/avatar.jpg",
        "status": "ACTIVE",
        "oauthProvider": null,
        "roles": [
          "ROLE_CREATOR",
          "ROLE_USER"
        ],
        "createdAt": "2026-05-25T21:57:00+07:00"
      }
    }
    ```
