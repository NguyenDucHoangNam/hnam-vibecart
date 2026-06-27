# 🔔 Thiết kế Kỹ thuật - Phân hệ 7: Hệ thống Thông báo Thời gian thực

## 1. Tổng quan Kiến trúc

```
┌──────────────────────────────────────────────────────────────────────┐
│                        Frontend (Next.js)                            │
│  ┌─────────────┐  ┌──────────────────┐  ┌─────────────────────────┐ │
│  │ Header.tsx   │  │ NotificationCtx  │  │ /notifications page    │ │
│  │ (Bell+Drop)  │  │ (WebSocket sub)  │  │ (Full page view)       │ │
│  └──────┬───────┘  └────────┬─────────┘  └───────────┬─────────────┘ │
│         │                   │                         │              │
│         └───────────────────┼─────────────────────────┘              │
│                             │                                        │
│                   ┌─────────▼──────────┐                            │
│                   │  useWebSocket()    │                            │
│                   │  STOMP Client      │                            │
│                   └─────────┬──────────┘                            │
└─────────────────────────────┼────────────────────────────────────────┘
                              │ WebSocket (STOMP)
┌─────────────────────────────▼────────────────────────────────────────┐
│                        Backend (Spring Boot)                         │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │                  common/websocket/                            │    │
│  │  WebSocketConfig │ EventListener │ DynamicRedisSubManager    │    │
│  │  WebSocketLifecycleHandler interface                         │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌────────────────┐    ┌──────────────────────────────────────┐     │
│  │ FollowService  │    │     modules/notification/             │     │
│  │  toggleFollow() ├──►│  Consumer → Service → Repository     │     │
│  └────────────────┘    │  Controller (REST API)                │     │
│        Kafka           │  Mapper                               │     │
│                        └──────────┬──────────┬────────────────┘     │
│                                   │          │                       │
│                          ┌────────▼──┐  ┌────▼─────┐                │
│                          │ MongoDB   │  │  Redis   │                │
│                          │ (persist) │  │ (cache)  │                │
│                          └───────────┘  └──────────┘                │
└──────────────────────────────────────────────────────────────────────┘
```

## 2. Cấu trúc Module

### 2.1 Backend Package Structure

```
com.vibecart.api/
├── common/websocket/
│   ├── WebSocketConfig.java              (Shared STOMP config)
│   ├── WebSocketLifecycleHandler.java    (Interface for module hooks)
│   ├── WebSocketEventListener.java       (Dispatches to all handlers)
│   ├── DynamicRedisSubscriptionManager.java (Generic Redis Pub/Sub)
│   └── RealtimeRedisConfig.java          (Shared ObjectMapper + Container)
│
├── modules/chat/config/
│   ├── ChatWebSocketHandler.java         (Implements WebSocketLifecycleHandler)
│   ├── ChatRedisMessageRouter.java       (Implements MessageListener)
│   └── RedisChatConfig.java              (ChatEvent RedisTemplate only)
│
└── modules/notification/
    ├── controller/
    │   └── NotificationController.java
    ├── consumer/
    │   └── InAppNotificationConsumer.java
    ├── service/
    │   ├── NotificationService.java
    │   └── impl/NotificationServiceImpl.java
    ├── repository/
    │   ├── NotificationRepository.java
    │   ├── NotificationPreferenceRepository.java
    │   └── PushSubscriptionRepository.java
    ├── entity/
    │   ├── Notification.java
    │   ├── NotificationType.java
    │   ├── NotificationPreference.java
    │   └── PushSubscription.java
    ├── dto/
    │   ├── event/InAppNotificationEvent.java
    │   ├── request/UpdatePreferencesRequest.java
    │   └── response/
    │       ├── NotificationResponse.java
    │       └── PreferencesResponse.java
    └── mapper/
        └── NotificationMapper.java
```

### 2.2 Frontend Structure

```
frontend/src/
├── types/notification.ts
├── services/notification.service.ts
├── context/NotificationContext.tsx
├── app/notifications/page.tsx
├── components/common/Header.tsx          (Bell icon + dropdown)
└── public/
    ├── sounds/notification.wav
    └── sw.js                              (Service Worker - Web Push)
```

## 3. Luồng Dữ liệu Chi tiết

### 3.1 Follow → Notification Flow

```
1. User A clicks "Follow" on User B
2. FollowServiceImpl.toggleFollow()
   ├── Save Follow entity (PostgreSQL)
   ├── FeedFanoutService.onFollow()
   └── KafkaTemplate.send("in-app-notification-events", event)

3. InAppNotificationConsumer.consume(event)
   └── NotificationServiceImpl.processAndBroadcast(event)
       ├── [OPT #4] Check user preferences → skip if inApp=false
       ├── [OPT #2] Dedup check (5 min window) → skip if duplicate
       ├── MongoDB: Save Notification document
       ├── [OPT #1] Redis: INCR unread count
       └── WebSocket: convertAndSendToUser(recipientUsername, "/queue/notifications", response)

4. Frontend NotificationContext
   ├── WebSocket callback → receive notification
   ├── Prepend to notifications array
   ├── Increment unreadCount
   ├── [OPT #4] Check sendSound flag → play sound if true
   └── Show toast
```

### 3.2 Unread Count Caching (Redis)

```
Key:    "notification:unread:{userId}"
Type:   String (integer)
TTL:    1 day

Operations:
- INCR    → New notification arrives
- DECR    → Mark single as read
- SET "0" → Mark all as read / Delete all
- GET     → Frontend requests count (cache hit)
- Fallback: COUNT query MongoDB (cache miss → SET + TTL)
```

## 4. Tối ưu Hiệu năng

### 4.1 Deduplication (#2)
- Window: 5 phút
- Query: `existsByActorIdAndRecipientIdAndTypeAndCreatedAtAfter()`
- Ngăn spam follow/unfollow/follow tạo nhiều notification trùng

### 4.2 Optimistic UI (#3)
- Frontend update state trước khi gọi API
- Rollback nếu API response lỗi
- Áp dụng cho: markAsRead, delete, markAllAsRead, deleteAll

### 4.3 Notification Preferences (#4)
- MongoDB collection `notification_preferences`
- Per-type toggle: inApp, sound, push
- Default preferences auto-generated nếu chưa có
- Check trong `processAndBroadcast()` trước khi save/push

## 5. WebSocket Infrastructure Refactoring

### 5.1 Before (Tightly Coupled)
```
chat/config/WebSocketConfig.java     → hardcoded /ws-chat
chat/config/WebSocketEventListener   → trực tiếp gọi presenceService
chat/config/DynamicRedisSubscriptionManager → hardcoded "chat:user:"
```

### 5.2 After (Loosely Coupled)
```
common/websocket/WebSocketConfig     → /ws + /ws-chat (backward compat)
common/websocket/WebSocketEventListener → gọi List<WebSocketLifecycleHandler>
common/websocket/DynamicRedisSubscriptionManager → generic (channelPrefix + listener)

chat/config/ChatWebSocketHandler     → implements WebSocketLifecycleHandler
chat/config/ChatRedisMessageRouter   → implements MessageListener
```

Các module mới (notification, etc.) chỉ cần implement `WebSocketLifecycleHandler` và được auto-inject bởi Spring.

## 6. MongoDB Collections & Indexes

| Collection | Indexes | TTL |
|------------|---------|-----|
| `notifications` | `{ recipient_id: 1, created_at: -1 }` (compound) | 90 ngày (`created_at`) |
| `notification_preferences` | `{ user_id: 1 }` (unique) | — |
| `push_subscriptions` | `{ user_id: 1 }` | — |
