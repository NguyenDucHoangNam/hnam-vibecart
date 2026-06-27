# 🔔 Đặc tả API - Phân hệ 7: Thông báo Thời gian thực (Realtime Notification)

Tài liệu này đặc tả hợp đồng tích hợp API toàn diện cho hệ thống Thông báo Thời gian thực, bao gồm WebSocket push, REST API quản lý, và cài đặt thông báo.

---

## 🌐 1. WebSocket Notification Channel

### 1.1 Điểm kết nối
*   **Endpoint:** `ws://localhost:8080/ws` (dùng chung với Chat)
*   **Backward compat:** `ws://localhost:8080/ws-chat`
*   **Xác thực:** JWT Token trong STOMP CONNECT header

### 1.2 Nhận thông báo realtime
*   **Destination:** `/user/queue/notifications`
*   **Payload nhận được (JSON):**
    ```json
    {
      "id": "66a1b2c3d4e5f6789012abcd",
      "type": "FOLLOW",
      "content": "Hùng đã bắt đầu theo dõi bạn",
      "read": false,
      "createdAt": "2026-06-27T10:30:00Z",
      "actor": {
        "id": "user-id-123",
        "username": "hung123",
        "fullName": "Hùng Nguyễn",
        "avatarUrl": "https://..."
      },
      "referenceId": null,
      "sendSound": true
    }
    ```
*   **Notification Types:** `FOLLOW`, `LIKE`, `COMMENT`, `ORDER_PAID`, `ORDER_DELIVERED`

---

## 📡 2. Kafka Event Pipeline

### 2.1 Topic: `in-app-notification-events`
*   **Partitions:** 3
*   **Consumer Group:** `vibecart-in-app-notification-group`
*   **Event Schema (InAppNotificationEvent):**
    ```json
    {
      "eventId": "uuid-string",
      "recipientId": "target-user-id",
      "recipientUsername": "targetUser",
      "actorId": "actor-user-id",
      "actorUsername": "actorUser",
      "actorFullName": "Hùng Nguyễn",
      "actorAvatarUrl": "https://...",
      "type": "FOLLOW",
      "referenceId": null,
      "content": "Hùng Nguyễn đã bắt đầu theo dõi bạn"
    }
    ```

### 2.2 Luồng xử lý
```
Producer (FollowServiceImpl) → Kafka Topic → Consumer (InAppNotificationConsumer)
  → Dedup check (5 phút) → Preferences check → MongoDB save → Redis INCR → WebSocket push
```

---

## 📋 3. REST API Endpoints

Tất cả endpoint yêu cầu **Bearer Token Authentication**.

### 3.1 Lấy danh sách thông báo

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/notifications` | Lấy danh sách thông báo (phân trang) |

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `page` | int | 0 | Trang hiện tại |
| `size` | int | 10 | Số lượng mỗi trang |
| `readStatus` | string | ALL | Filter: `ALL` hoặc `UNREAD` |

**Response (200 OK):**
```json
{
  "code": 1000,
  "message": "Lấy danh sách thông báo thành công",
  "result": {
    "content": [
      {
        "id": "66a1b2c3d4e5f6789012abcd",
        "type": "FOLLOW",
        "content": "Hùng đã bắt đầu theo dõi bạn",
        "read": false,
        "createdAt": "2026-06-27T10:30:00Z",
        "actor": {
          "id": "user-id-123",
          "username": "hung123",
          "fullName": "Hùng Nguyễn",
          "avatarUrl": "https://..."
        },
        "referenceId": null,
        "sendSound": true
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 25,
    "totalPages": 3,
    "last": false
  }
}
```

### 3.2 Đếm thông báo chưa đọc

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/notifications/unread-count` | Đếm thông báo chưa đọc (cached Redis) |

**Response (200 OK):**
```json
{ "code": 1000, "result": 5 }
```

### 3.3 Đánh dấu đã đọc

| Method | Path | Description |
|--------|------|-------------|
| `PUT` | `/api/v1/notifications/{id}/read` | Đánh dấu 1 thông báo đã đọc |
| `PUT` | `/api/v1/notifications/read-all` | Đánh dấu tất cả đã đọc |

### 3.4 Xóa thông báo

| Method | Path | Description |
|--------|------|-------------|
| `DELETE` | `/api/v1/notifications/{id}` | Xóa 1 thông báo |
| `DELETE` | `/api/v1/notifications` | Xóa tất cả thông báo |

### 3.5 Cài đặt thông báo (Preferences)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/notifications/preferences` | Lấy cài đặt hiện tại |
| `PUT` | `/api/v1/notifications/preferences` | Cập nhật cài đặt |

**PUT Request Body:**
```json
{
  "preferences": {
    "FOLLOW": { "inApp": true, "sound": true, "push": true },
    "LIKE": { "inApp": true, "sound": false, "push": false },
    "COMMENT": { "inApp": true, "sound": true, "push": true },
    "ORDER_PAID": { "inApp": true, "sound": true, "push": true },
    "ORDER_DELIVERED": { "inApp": true, "sound": true, "push": true }
  }
}
```

**Response (200 OK):**
```json
{
  "code": 1000,
  "message": "Đã cập nhật cài đặt thông báo",
  "result": {
    "preferences": {
      "FOLLOW": { "inApp": true, "sound": true, "push": true },
      "LIKE": { "inApp": true, "sound": false, "push": false }
    }
  }
}
```

---

## 🗄️ 4. MongoDB Schema

### 4.1 Collection: `notifications`

```json
{
  "_id": "ObjectId",
  "recipient_id": "String (indexed)",
  "actor_id": "String",
  "actor_username": "String",
  "actor_full_name": "String",
  "actor_avatar_url": "String",
  "type": "String",
  "reference_id": "String",
  "content": "String",
  "is_read": "Boolean (default: false)",
  "created_at": "Instant (TTL: 90 ngày)"
}
```

**Indexes:**
- Compound: `{ recipient_id: 1, created_at: -1 }`
- TTL: `created_at` (expireAfter: 90 ngày)

### 4.2 Collection: `notification_preferences`

```json
{
  "_id": "ObjectId",
  "user_id": "String (unique indexed)",
  "preferences": {
    "FOLLOW": { "inApp": true, "sound": true, "push": true },
    "LIKE": { "inApp": true, "sound": false, "push": false }
  }
}
```

### 4.3 Collection: `push_subscriptions`

```json
{
  "_id": "ObjectId",
  "user_id": "String (indexed)",
  "endpoint": "String",
  "p256dh_key": "String",
  "auth_key": "String",
  "created_at": "Instant"
}
```

---

## ⚡ 5. Tối ưu hiệu năng

### 5.1 Redis Cache Unread Count
- Key: `notification:unread:{userId}`
- INCR khi nhận notification mới
- DECR khi mark read
- SET 0 khi mark all read
- TTL: 1 ngày (fallback query MongoDB)

### 5.2 Deduplication
- Kiểm tra notification trùng (cùng actor + recipient + type) trong 5 phút gần nhất
- Tránh spam follow/unfollow/follow

### 5.3 Optimistic UI (Frontend)
- Update UI ngay lập tức khi mark read / delete
- Rollback nếu API thất bại

---

## 🛡️ 6. Error Codes

| Code | Name | HTTP Status | Message |
|------|------|-------------|---------|
| 6001 | NOTIFICATION_NOT_FOUND | 404 | Thông báo không tồn tại |
| 6002 | NOTIFICATION_ACCESS_DENIED | 403 | Bạn không có quyền truy cập thông báo này |
