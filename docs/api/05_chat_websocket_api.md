# 🔌 Đặc tả API - Phân hệ 5: Nhắn tin Thời gian thực (Realtime Chat)

Tài liệu này đặc tả hợp đồng tích hợp API toàn diện cho hệ thống Nhắn tin Thời gian thực (Realtime Chat - 1-1 & Group Chat), bao gồm các giao thức truyền tin qua **WebSocket STOMP Destinations** và các REST APIs bổ trợ.

> [!NOTE]
> Để xem thiết kế kiến trúc kỹ thuật (DB Schema, Redis Pub/Sub, Presence Engine), tham khảo: [05_realtime_chat_design.md](../technical/05_realtime_chat_design.md).
> Để xem đặc tả nghiệp vụ (User Flows & Business Rules), tham khảo: [05_realtime_chat.md](../business/05_realtime_chat.md).

---

## 🌐 1. Giao thức WebSocket STOMP

Hệ thống sử dụng giao thức **STOMP (Simple Text Orientated Messaging Protocol)** chạy trên nền WebSockets để đảm bảo truyền tải tin nhắn thời gian thực full-duplex hiệu năng cao.

### 1.1 Điểm kết nối (WebSocket Endpoint)
*   **Dev Path:** `ws://localhost:8080/ws-chat`
*   **Prod Path:** `wss://chat.vibecart.com/ws-chat`
*   **Cơ chế bảo mật:** JWT Token bắt buộc phải gửi đính kèm trong frame **CONNECT** ở trường header `Authorization`.
    ```text
    CONNECT
    accept-version:1.1,1.2
    heart-beat:10000,10000
    Authorization:Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
    ```

---

## 📥 2. Đặc tả Kênh Đăng ký (STOMP Subscribe Destinations)

Frontend đăng ký lắng nghe (Subscribe) các kênh sau để nhận tin nhắn và cập nhật thời gian thực từ Backend:

### 2.1 Nhận tin nhắn cá nhân (Private Message Channel)
*   **Destination:** `/user/queue/messages`
*   **Payload nhận được (JSON):**
    ```json
    {
      "id": "60b8d2a18f3b2c12a45d8b8b",
      "conversationId": "60b8d29f8f3b2c12a45d8b8a",
      "senderId": "1002",
      "type": "TEXT", // TEXT, IMAGE, VIDEO, DOCUMENT, PRODUCT, ORDER
      "content": "Chào bạn, đây là tai nghe Sony chính hãng nhé!",
      "attachmentMetadata": null, // Hoặc đối tượng chứa metadata tệp đính kèm
      "readBy": [
        {
          "userId": "1002",
          "readAt": "2026-05-27T16:22:00Z"
        }
      ],
      "createdAt": "2026-05-27T16:22:00Z"
    }
    ```

### 2.2 Nhận cập nhật danh sách hội thoại (Conversation Update Channel)
*   *Lưu ý:* Kênh `/user/queue/conversations` hiện tại chưa kích hoạt ở Backend. Frontend tự động cập nhật preview `lastMessage` và số lượng `unreadCounts` trực tiếp khi nhận sự kiện tin nhắn qua kênh `/user/queue/messages` hoặc `/topic/chat.{conversationId}`, hoặc tải lại từ REST API.

### 2.3 Kênh sự kiện phòng chat (Group Chat & Realtime Events Topic)
Frontend đăng ký lắng nghe sự kiện trực tiếp trong phòng chat cụ thể (cả 1-1 và Group Chat) thông qua các kênh:
*   **Nhận tin nhắn của phòng:** `/topic/chat.{conversationId}`
    *   *Payload:* Xem cấu trúc tin nhắn ở mục 2.1.
*   **Nhận trạng thái soạn thảo của thành viên:** `/topic/chat.{conversationId}/typing`
    *   *Payload (JSON):*
        ```json
        {
          "conversationId": "60b8d29f8f3b2c12a45d8b8a",
          "username": "creator_vibecart",
          "isTyping": true // true là đang gõ, false là đã dừng gõ
        }
        ```
*   **Nhận thông báo đã xem (Seen Receipt):** `/topic/chat.{conversationId}/seen`
    *   *Payload (JSON):*
        ```json
        {
          "conversationId": "60b8d29f8f3b2c12a45d8b8a",
          "userId": "1001",
          "readAt": "2026-05-27T16:25:00Z"
        }
        ```

### 2.4 Kênh sự kiện cá nhân hỗ trợ Direct Chat
Frontend cũng có thể đăng ký các hàng đợi cá nhân để xử lý sự kiện Direct Chat:
*   **Đang soạn thảo riêng:** `/user/queue/typing`
*   **Đã xem riêng:** `/user/queue/seen`

---

## 📤 3. Đặc tả Kênh Phát tin (STOMP Send Destinations)

Frontend phát các hành động (Publish) lên Backend qua các kênh sau:

### 3.1 Gửi tin nhắn mới (Send Message)
*   **Destination:** `/app/chat.sendMessage`
*   **Payload gửi đi (JSON):**
    ```json
    {
      "conversationId": "60b8d29f8f3b2c12a45d8b8a",
      "type": "TEXT", // TEXT, IMAGE, VIDEO, DOCUMENT, PRODUCT, ORDER
      "content": "Chào bạn, đây là tai nghe Sony chính hãng nhé!",
      "attachmentMetadata": { // Cần thiết đối với IMAGE, VIDEO, DOCUMENT, PRODUCT, ORDER
        "fileUrl": "https://vibecart-bucket.s3.amazonaws.com/chat/attachments/sony.jpg",
        "fileName": "sony.jpg",
        "fileSize": 1048576,
        "mimeType": "image/jpeg",
        "cardId": "prod_201" // Gắn ID sản phẩm/đơn hàng nếu type là PRODUCT/ORDER
      }
    }
    ```

### 3.2 Phát sự kiện Đang gõ chữ (Publish Typing Status)
*   **Destination:** `/app/chat.typing`
*   **Payload gửi đi (JSON):**
    ```json
    {
      "conversationId": "60b8d29f8f3b2c12a45d8b8a",
      "isTyping": true
    }
    ```

### 3.3 Duy trì trạng thái trực tuyến (Presence Heartbeat Ping)
*   **Destination:** `/app/chat.ping`
*   **Payload gửi đi:** `{}` (Gói tin rỗng phát mỗi 30 giây để duy trì trạng thái ONLINE trên Redis)

### 3.4 Báo nhận đã xem tin nhắn (Send Seen Receipt)
*   **Destination:** `/app/chat.seen`
*   **Payload gửi đi (JSON):**
    ```json
    {
      "conversationId": "60b8d29f8f3b2c12a45d8b8a"
    }
    ```
*   **Hành vi Backend:** Reset `unreadCounts[currentUserId]` về 0, cập nhật `readBy` cho các tin nhắn chưa đọc, broadcast `READ_RECEIPT` event qua Redis Pub/Sub tới các thành viên khác.

---

## 🔌 4. Các Endpoints REST bổ trợ (Supporting Chat REST APIs)

### 4.1 Khởi tạo Hội thoại mới (Create or Get Conversation)
*   **Method:** `POST`
*   **Path:** `/api/v1/chat/conversations`
*   **Auth Level:** `Require Bearer Token` — `@PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")`
*   **Request Body:**
    ```json
    {
      "type": "GROUP", // DIRECT hoặc GROUP
      "name": "Nhóm Hỗ trợ Khách hàng VIP", // Bắt buộc nếu là GROUP, null nếu là DIRECT
      "memberIds": ["1002", "2005"] // Danh sách ID các thành viên muốn kết nối
    }
    ```
*   **Response Success (HTTP 201 Created):**
    ```json
    {
      "code": 1000,
      "message": "Khởi tạo cuộc hội thoại thành công",
      "result": {
        "id": "60b8d29f8f3b2c12a45d8b8a",
        "type": "GROUP",
        "name": "Nhóm Hỗ trợ Khách hàng VIP",
        "memberIds": ["1001", "1002", "2005"],
        "unreadCounts": {
          "1001": 0,
          "1002": 0,
          "2005": 0
        },
        "members": [
          {
            "id": "1001",
            "username": "user1",
            "email": "user1@vibecart.com",
            "roles": "USER"
          },
          {
            "id": "1002",
            "username": "user2",
            "email": "user2@vibecart.com",
            "roles": "CREATOR"
          }
        ],
        "createdAt": "2026-05-27T16:22:00Z",
        "updatedAt": "2026-05-27T16:22:00Z"
      }
    }
    ```

---

### 4.2 Lấy danh sách hội thoại gần đây (Get Conversations)
*   **Method:** `GET`
*   **Path:** `/api/v1/chat/conversations`
*   **Auth Level:** `Require Bearer Token` — `@PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")`
*   **Response Success (HTTP 200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy danh sách hội thoại thành công",
      "result": [
        {
          "id": "60b8d29f8f3b2c12a45d8b8a",
          "type": "GROUP",
          "name": "Nhóm Creator Sáng Tạo VibeCart",
          "avatarUrl": "https://vibecart.com/assets/group-avatar.jpg",
          "memberIds": ["1001", "1002", "2005"],
          "unreadCounts": {
            "1001": 2,
            "1002": 0,
            "2005": 5
          },
          "lastMessage": {
            "messageId": "60b8d2a18f3b2c12a45d8b8b",
            "senderId": "1002",
            "content": "Chào cả nhà, em gửi file thiết kế sản phẩm mới nhé!",
            "type": "DOCUMENT",
            "createdAt": "2026-05-27T15:30:00Z"
          },
          "members": [
            {
              "id": "1001",
              "username": "user1",
              "email": "user1@vibecart.com",
              "roles": "USER"
            },
            {
              "id": "1002",
              "username": "user2",
              "email": "user2@vibecart.com",
              "roles": "CREATOR"
            }
          ],
          "createdAt": "2026-05-27T15:00:00Z",
          "updatedAt": "2026-05-27T15:30:00Z"
        }
      ]
    }
    ```

---

### 4.3 Tải lịch sử tin nhắn trong hội thoại (Get Messages)
*   **Method:** `GET`
*   **Path:** `/api/v1/chat/conversations/{conversationId}/messages`
*   **Auth Level:** `Require Bearer Token` — `@PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")`
*   **Bảo mật tầng Service:** Kiểm tra `currentUserId ∈ conversation.memberIds` (bypass cho ADMIN).
*   **Query Parameters:**
    *   `page`: Số trang (Ví dụ: `page=0`, sắp xếp giảm dần theo thời gian nhận tin)
    *   `size`: Số lượng (Ví dụ: `size=30`, mặc định `20`)
*   **Response Success (HTTP 200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy danh sách tin nhắn thành công",
      "result": {
        "content": [
          {
            "id": "60b8d2a18f3b2c12a45d8b8b",
            "conversationId": "60b8d29f8f3b2c12a45d8b8a",
            "senderId": "1002",
            "content": "Chào cả nhà!",
            "type": "TEXT",
            "attachmentMetadata": null,
            "readBy": [
              {
                "userId": "1002",
                "readAt": "2026-05-27T15:30:00Z"
              }
            ],
            "createdAt": "2026-05-27T15:30:00Z"
          }
        ],
        "page": 0,
        "size": 30,
        "totalPages": 5,
        "totalElements": 150,
        "last": false
      }
    }
    ```

---

### 4.4 Đánh dấu đã đọc hội thoại (Mark as Read)

Hệ thống hỗ trợ **hai cơ chế** đánh dấu đã đọc:

1.  **Implicit (REST):** Hành vi đọc tin nhắn xảy ra tự động khi gọi API tải tin nhắn phòng (`GET /api/v1/chat/conversations/{conversationId}/messages`). Hệ thống tự động reset `unreadCounts` của user đó về 0.

2.  **Explicit (WebSocket):** Client gửi STOMP message tới `/app/chat.seen` với payload `{ "conversationId": "..." }` để đánh dấu đã đọc realtime mà không cần reload trang. Hệ thống reset `unreadCounts`, cập nhật `readBy`, và broadcast `READ_RECEIPT` event tới các thành viên khác.

---

### 4.5 Yêu cầu sinh link tải tệp tin lên S3/MinIO trực tiếp (Get Pre-signed Upload URL)
*   **Method:** `POST`
*   **Path:** `/api/v1/chat/attachments/presigned-url`
*   **Auth Level:** `Require Bearer Token` — `@PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")`
*   **Request Body:**
    ```json
    {
      "fileName": "photo_2026.png",
      "fileSize": 1548200, // bytes (Trường bắt buộc để kiểm duyệt kích thước tối đa 20MB)
      "contentType": "image/png"
    }
    ```
*   **Response Success (HTTP 200 OK):**
    ```json
    {
      "code": 1000,
      "result": {
        "uploadUrl": "https://vibecart-bucket.s3.amazonaws.com/chat/photo_2026.png?AWSAccessKeyId=AKIA...&Expires=1780968000&Signature=...",
        "fileUrl": "https://vibecart-bucket.s3.amazonaws.com/chat/photo_2026.png"
      }
    }
    ```

---

### 4.6 Kiểm tra trạng thái trực tuyến của người dùng (Get User Presence)
*   **Method:** `GET`
*   **Path:** `/api/v1/chat/presence/{userId}`
*   **Auth Level:** `Require Bearer Token` — `@PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")`
*   **Path Parameters:**
    *   `userId`: ID của người dùng cần kiểm tra trạng thái (dạng String)
*   **Response Success (HTTP 200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy trạng thái trực tuyến thành công",
      "result": {
        "userId": "1002",
        "status": "ONLINE", // ONLINE hoặc OFFLINE
        "lastActiveAt": "2026-05-27T16:22:00Z" // null nếu đang ONLINE
      }
    }
    ```

---

### 4.7 Lấy danh sách bạn bè đang hoạt động (Get Active Users)
*   **Method:** `GET`
*   **Path:** `/api/v1/chat/presence/active`
*   **Auth Level:** `Require Bearer Token` — `@PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")`
*   **Mô tả:** Trả về danh sách những người dùng mà user hiện tại đang theo dõi (following) và đang có trạng thái `ONLINE`.
*   **Response Success (HTTP 200 OK):**
    ```json
    {
      "code": 1000,
      "message": "Lấy danh sách người dùng đang hoạt động thành công",
      "result": [
        {
          "userId": "1002",
          "username": "creator_vibecart",
          "fullName": "Nguyễn Văn Creator",
          "avatarUrl": "https://vibecart.com/avatars/1002.jpg"
        }
      ]
    }
    ```
