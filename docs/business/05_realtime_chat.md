# 💼 Tài liệu Nghiệp vụ - Phân hệ 5: Nhắn tin Thời gian thực (Realtime Chat)

Phân hệ Nhắn tin Thời gian thực (Realtime Chat) đóng vai trò là hạ tầng kết nối, hỗ trợ Shopper và Creator tương tác tức thời về sản phẩm/đơn hàng, đồng thời thúc đẩy tương tác cộng đồng trong các nhóm chat (Group Chat) trên nền tảng mạng xã hội **VibeCart**.

> [!NOTE]
> Để xem thiết kế kiến trúc kỹ thuật (DB Schema, Redis Pub/Sub, Presence Engine), tham khảo: [05_realtime_chat_design.md](../technical/05_realtime_chat_design.md).
> Để xem hợp đồng tích hợp API (REST Endpoints & WebSocket Payloads), tham khảo: [05_chat_websocket_api.md](../api/05_chat_websocket_api.md).

---

## 👥 1. Các Đối Tượng Hệ Thống & Vai trò (System Actors & Roles)

Các chủ thể tham gia tương tác trực tiếp trong không gian hội thoại:

| Vai trò (Role) | Ký hiệu hệ thống | Tương tác Nghiệp vụ Chat & Group Chat |
| :--- | :--- | :--- |
| **Người khởi xướng** | Shopper hoặc Creator | • Bắt đầu cuộc trò chuyện 1-1 từ trang chi tiết sản phẩm hoặc chi tiết đơn hàng.<br>• Tạo phòng chat nhóm mới và đặt tên/ảnh nhóm. |
| **Thành viên Nhóm** | Khách hàng có tài khoản | • Nhận và gửi tin nhắn (văn bản, hình ảnh, tài liệu) thời gian thực.<br>• Tích chọn đính kèm thẻ sản phẩm/đơn hàng để thảo luận chung.<br>• Xem danh sách những người đã đọc tin nhắn trong nhóm.<br>• Rời khỏi nhóm chat. |
| **Trưởng nhóm (Creator/Admin)**| Người sáng lập nhóm | • Quản lý cài đặt nhóm chat.<br>• Thêm thành viên mới hoặc trục xuất thành viên vi phạm khỏi nhóm. |

---

## 🔄 2. Luồng Nghiệp vụ Cốt lõi (Core Business Flows)

### 2.1 Luồng Khởi tạo Hội thoại & Đính kèm Thẻ (Conversation & Card Attachment Flow)
Khi Shopper có thắc mắc về sản phẩm hoặc đơn hàng, họ có thể mở chat trực tiếp, hệ thống tự động đính kèm "Thẻ ngữ cảnh" (Context Card) để cuộc đối thoại diễn ra thuận lợi.

```mermaid
sequenceDiagram
    autonumber
    Actor Shopper as Người mua (Shopper)
    participant System as Hệ Thống VibeCart
    participant DB as MongoDB (Chat Store)
    Actor Creator as Nhà sáng tạo (Creator)

    Shopper->>System: 1. Click "Chat với Seller" từ trang Sản phẩm/Đơn hàng
    System->>DB: 2. Tra cứu hoặc khởi tạo cuộc hội thoại 1-1 giữa Shopper & Creator
    System->>System: 3. Đóng gói Thẻ Sản phẩm (Product Card) hoặc Thẻ Đơn hàng (Order Card)
    System-->>Shopper: 4. Mở cửa sổ Chat, tự động soạn thảo Thẻ đính kèm trong ô chat
    Shopper->>System: 5. Nhấn "Gửi"
    System->>DB: 6. Lưu tin nhắn kèm Metadata của Thẻ vào MongoDB
    System-->>Creator: 7. Đẩy tin nhắn đính kèm thẻ sang WebSocket của Creator thời gian thực
```

---

### 2.2 Luồng Gửi và Nhận Tin nhắn Thời gian thực (Realtime Send & Receive Flow)
Đảm bảo tốc độ truyền tin tức thời (Sub-100ms) trên các kênh WebSocket/STOMP.

```mermaid
sequenceDiagram
    autonumber
    Actor Sender as Người gửi (User A)
    participant Server as Spring Boot Chat Server
    participant Redis as Redis Pub/Sub (Broker)
    participant DB as MongoDB Store
    Actor Receiver as Người nhận (User B)

    Sender->>Server: 1. Gửi tin nhắn qua WebSocket STOMP (SEND /app/chat.sendMessage)
    
    rect rgb(240, 248, 255)
        Note over Server, DB: Xử lý ghi đĩa bất đồng bộ
        Server->>DB: 2. Lưu tin nhắn vào MongoDB (Trạng thái: SENT)
    end

    Server->>Redis: 3. Phát tin nhắn đến Redis Channel 'chat:user:UserB'
    Redis-->>Server: 4. Server của User B nhận tin nhắn từ Redis Channel
    Server-->>Receiver: 5. Đẩy tin nhắn xuống kết nối WebSocket của User B (SUBSCRIBE /user/queue/messages)
    
    Receiver->>Server: 6. Client User B gửi ACK "Đã đọc" (Seen Receipt)
    Server->>DB: 7. Cập nhật mốc thời gian đọc của User B vào MongoDB
    Server-->>Sender: 8. Thông báo qua WebSocket của User A: Tin nhắn đã chuyển sang 'SEEN/READ'
```

---

### 2.3 Luồng Tải lên tệp trực tiếp lên S3/MinIO qua Pre-signed URL (Client-Direct Upload Flow)
Để Spring Boot Server không bị nghẽn RAM khi nhiều người dùng gửi tệp đa phương tiện (ảnh, video, PDF) cùng lúc, hệ thống áp dụng luồng tải thẳng (Direct Upload):

```mermaid
sequenceDiagram
    autonumber
    Actor Client as Trình duyệt / Mobile Client
    participant Server as Spring Boot API
    participant S3 as AWS S3 / MinIO Store
    participant WS as WebSocket STOMP

    Client->>Server: 1. Yêu cầu tải tệp (Tên file, kích thước, loại tệp)
    Server->>Server: 2. Kiểm tra định dạng hợp lệ (Max 20MB, đuôi jpg/png/mp4/pdf)
    Server->>Server: 3. Sinh Pre-signed URL bảo mật có hiệu lực trong 5 phút
    Server-->>Client: 4. Trả về Pre-signed Upload URL
    
    Client->>S3: 5. Thực hiện HTTP PUT tải trực tiếp tệp từ Client lên S3/MinIO
    S3-->>Client: 6. Tải lên thành công (HTTP 200 OK)
    
    Client->>WS: 7. Phát tin nhắn qua WebSocket chứa S3 Object URL để chia sẻ vào cuộc chat
```

---

## 🛡️ 3. Ràng buộc Nghiệp vụ & Cơ chế Đồng bộ (Enterprise Chat Rules & Sync Constraints)

### 3.1 Cơ chế Đồng bộ Số lượng tin nhắn chưa đọc (Unread Count Sync)
Để hiển thị huy hiệu đỏ (Badge notification) số lượng tin nhắn chưa đọc trên từng cuộc hội thoại một cách chính xác mà không tốn tài nguyên hệ thống:
*   **Cấu trúc dữ liệu:** Tài liệu Conversation lưu trữ một Map động dạng: `unread_counts: { "userId_1": 3, "userId_2": 0 }`.
*   **Quy tắc cộng dồn:** Khi tin nhắn mới được gửi vào phòng chat, tất cả các thành viên khác trong phòng (ngoại trừ người gửi) sẽ được hệ thống tăng tự động `unread_counts` lên **+1**.
*   **Quy tắc xóa bộ đếm (Reset):** Khi một thành viên nhấp mở cửa sổ phòng chat đó, hệ thống thực hiện reset `unread_counts[userId]` của người đó về **0** ngay lập tức và đồng bộ số liệu qua WebSocket.

### 3.2 Cơ chế Hiện diện Trực tuyến & Chỉ báo Soạn thảo (Presence & Typing Indicators)
*   **Presence (Online/Offline):**
    *   Hệ thống xác định trạng thái Online thông qua việc duy trì kết nối WebSocket. Client gửi gói tin heartbeat ping mỗi 30 giây để duy trì trạng thái.
    *   Khi Shopper mở danh sách tin nhắn, trạng thái Online hiển thị chấm xanh lá. Nếu Offline, hiển thị thời gian hoạt động cuối cùng: *"Hoạt động 10 phút trước"*.
*   **Typing Indicator (Đang soạn thảo):**
    *   Khi người dùng gõ phím trong ô chat, Client gửi sự kiện `TYPING` lên WebSocket `/app/chat.typing`.
    *   Hệ thống phân phối nhanh sự kiện này tới các thành viên khác trong phòng.
    *   Dòng chữ *"Người dùng X đang soạn thảo..."* hiển thị và tự động biến mất sau **3 giây** nếu không nhận được sự kiện gõ phím tiếp theo (Throttle/Debounce mechanism).

### 3.3 Hạn mức Tệp tin đính kèm (Media Upload Constraints)
*   **Kích thước tối đa:** Cho phép tải lên các tệp tin đính kèm có dung lượng tối đa **20MB**.
*   **Định dạng cho phép:**
    *   *Hình ảnh/Video:* JPG, PNG, GIF, WEBP, MP4 (Tự động hiển thị trình xem đa phương tiện trực tiếp trong khung chat).
    *   *Tài liệu:* PDF, DOCX, XLSX, ZIP, RAR (Hiển thị dưới dạng thẻ tải tệp kèm kích thước).
    *   Tất cả các định dạng thực thi (như `.exe`, `.bat`, `.sh`) bị cấm hoàn toàn để đảm bảo an ninh hệ thống.
