# Thiết kế Kiến trúc Tích hợp Kafka trong VibeCart

Tài liệu này đặc tả chi tiết hạ tầng, cấu hình các Topic, cơ chế phát hành tin nhắn đáng tin cậy (**Transactional Outbox Pattern**) và các logic xử lý bất đồng bộ (**Asynchronous Consumers**) sử dụng Apache Kafka trong dự án **VibeCart**.

---

## 1. Tổng quan Hạ tầng Kafka

*   **Kiến trúc:** Kafka được thiết lập làm xương sống xử lý bất đồng bộ (Event-Driven Architecture) giữa các module độc lập trong cấu trúc Modular Monolith.
*   **Cấu hình Hạ tầng:**
    *   **Local (`application-local.yaml`):** Kết nối đến Bootstrap Server tại `localhost:9092`.
    *   **Sử dụng JsonSerializer/JsonDeserializer** để tự động tuần tự hóa và giải tuần tự hóa payload JSON của các Class Event (như `ProductSyncEvent`, `NotificationEvent`, `OrderPaidEvent`...).
*   **Topic Configuration (`KafkaTopicConfig.java`):** Tất cả các topic được cấu hình **3 Partitions** để hỗ trợ phân tải (load balancing) khi scale-up các consumer instances, và **1 Replica** cho môi trường phát triển cục bộ.

---

## 2. Luồng Dữ liệu Event-Driven qua Kafka

Sơ đồ dưới đây thể hiện toàn bộ luồng phát hành sự kiện (Producers), các Topic trung gian và các logic xử lý sự kiện phía nhận (Consumers):

```mermaid
graph TD
    subgraph "1. Producers & Transactional Outbox"
        ProductService[Product Service] -->|1. Save DB & Outbox PRODUCT| DB[(PostgreSQL)]
        AuthService[Auth Service] -->|1. Save DB & Outbox IAM| DB
        
        OutboxScheduler[OutboxScheduler (fixedDelay 500ms)] -->|2. Scan & Send Sync| KafkaBroker((Kafka Broker))
        IamOutboxScheduler[IamOutboxScheduler (fixedDelay 500ms)] -->|2. Scan & Send Sync| KafkaBroker
        
        OrderService[Order Service / Controller] -->|Direct Async Send| KafkaBroker
        ShortLinkService[ShortLink Service] -->|Direct Async Send| KafkaBroker
    end

    subgraph "2. Kafka Topics (3 Partitions each)"
        KafkaBroker --> T_Sync["product-sync-topic"]
        KafkaBroker --> T_Notif["notification-events"]
        KafkaBroker --> T_Paid["order-paid-topic"]
        KafkaBroker --> T_Clicks["affiliate-clicks-topic"]
        KafkaBroker --> T_Deliv["order-delivered-topic"]
        KafkaBroker --> T_Cancel["order-cancelled-topic"]
    end

    subgraph "3. Consumers & Event Processors"
        T_Sync --> SearchSyncConsumer[SearchSyncConsumer] -->|Sync document| ES[(Elasticsearch)]
        
        T_Notif --> NotificationListener[NotificationKafkaListener] -->|Exponential Retry / DLT| EmailService[Email Service - SMTP]
        
        T_Paid --> EmailWorker[EmailWorkerConsumer] -->|Send Invoice HTML| Client[Customer Mailbox]
        T_Paid --> AffiliateOrder1[AffiliateOrderConsumers] -->|Create Commission PENDING| DB
        
        T_Clicks --> ClickConsumer[AffiliateClickConsumer] -->|Buffer Batching 50 items/3s| DB
        
        T_Deliv --> AffiliateOrder2[AffiliateOrderConsumers] -->|Approve Commission| DB
        T_Cancel --> AffiliateOrder3[AffiliateOrderConsumers] -->|Reject Commission| DB
    end
```

---

## 3. Đặc tả Cấu hình các Topic

Các topic được khai báo tường minh dưới dạng Spring Beans trong `KafkaTopicConfig.java`:

| Tên Topic | Số Partitions | Hướng Sử Dụng (Dataflow) |
| :--- | :---: | :--- |
| `product-sync-topic` | 3 | Đồng bộ dữ liệu thay đổi của sản phẩm từ PostgreSQL sang Elasticsearch. |
| `notification-events` | 3 | Gửi các thông báo email bất đồng bộ (OTP, reset password...). |
| `order-paid-topic` | 3 | Phát hành khi đơn hàng được xác nhận đã thanh toán thành công. |
| `affiliate-clicks-topic` | 3 | Ghi nhận hành vi click vào link tiếp thị liên kết (affiliate) của người dùng. |
| `order-delivered-topic` | 3 | Phát hành khi đơn hàng được đơn vị vận chuyển giao thành công. |
| `order-cancelled-topic` | 3 | Phát hành khi đơn hàng bị hủy hoặc hoàn trả tiền. |

---

## 4. Cơ chế Phát hành Tin nhắn (Producers)

Hệ thống áp dụng hai cơ chế gửi tin nhắn khác nhau tùy thuộc vào mức độ quan trọng của nghiệp vụ:

### 4.1. Transactional Outbox Pattern (Đảm bảo Giao dịch Tin cậy)
Áp dụng cho module **Sản phẩm (Product Sync)** và **Bảo mật (OTP / Notification)** để giải quyết vấn đề "Dual-write" (ghi DB thành công nhưng gửi Message Broker lỗi hoặc ngược lại).

1.  **Giai đoạn ghi (Write):** Khi Service tạo/cập nhật sản phẩm hoặc sinh mã OTP, thay vì gửi trực tiếp vào Kafka, hệ thống sẽ tạo một đối tượng `OutboxEvent` (trạng thái `"PENDING"`) và lưu vào cơ sở dữ liệu PostgreSQL trong cùng một Transaction nghiệp vụ.
2.  **Giai đoạn đẩy (Publish):** Hai scheduler (`OutboxScheduler` và `IamOutboxScheduler`) chạy ngầm mỗi 500ms quét các bản ghi `"PENDING"`.
3.  **Đảm bảo At-least-once:** Scheduler gọi `kafkaTemplate.send(...).get()` để gửi **đồng bộ** (Synchronous Send). Khi Kafka Broker phản hồi xác nhận (ACK) thành công, trạng thái outbox mới được đổi sang `"PROCESSED"`.
4.  **Bảo toàn thứ tự (Order Preserving):** Nếu có lỗi mạng tạm thời, Scheduler sẽ dừng vòng lặp (`break`) giữ nguyên các tin nhắn tiếp theo ở trạng thái `"PENDING"` để đảm bảo các bản cập nhật sau không bị gửi đè lên bản cập nhật trước.

### 4.2. Gửi Bất đồng bộ Trực tiếp (Direct Asynchronous Send)
Áp dụng cho các sự kiện **Đặt hàng (Order Lifecycle)** và **Click liên kết (Affiliate Clicks)**.
*   Hệ thống gọi trực tiếp `kafkaTemplate.send(...)` bất đồng bộ (Asynchronous Send) không chờ phản hồi ACK để tối ưu hóa thời gian phản hồi API (latency dưới 10ms), đảm bảo trải nghiệm người dùng mượt mà nhất.

---

## 5. Cơ chế Tiêu thụ Tin nhắn (Consumers)

### 5.1. Đồng bộ hóa Elasticsearch (`SearchSyncConsumer`)
*   **Topic:** `product-sync-topic` (groupId: `search-sync-group`).
*   **Logic xử lý:** Nhận thông tin sản phẩm và phân loại theo trạng thái sự kiện:
    *   `CREATED`, `UPDATED`: Tự động tính toán lại khoảng giá biến thể (minPrice/maxPrice) của SKU đang hoạt động, tạo tài liệu `ProductDocument` và ghi đè (Index) vào Elasticsearch.
    *   `DELETED`: Xóa bỏ tài liệu tương ứng khỏi Elasticsearch.

### 5.2. Hàng đợi Gửi Email đáng tin cậy (`NotificationKafkaListener`)
*   **Topic:** `notification-events` (groupId: `vibecart-notification-group`).
*   **Chính sách Thử lại & Xử lý lỗi (Retry & DLQ):**
    *   Tích hợp annotation `@RetryableTopic` của Spring Kafka: Thử lại tối đa **4 lần** (1 lần chính + 3 lần retry).
    *   **Exponential Backoff:** Khoảng thời gian chờ tăng dần theo cấp số nhân (5 giây, 10 giây, 20 giây) để tránh spam/dồn dập máy chủ gửi mail SMTP khi gặp sự cố nghẽn mạng tạm thời.
    *   **Dead Letter Queue (DLQ):** Nếu thất bại toàn bộ 4 lần, message được tự động chuyển sang Topic DLT (`notification-events-dlt`). `@DltHandler` sẽ bắt tin nhắn này và ghi log lỗi mức độ nguy hiểm (`CRITICAL`) để quản trị viên đối soát/gửi mail thủ công.

### 5.3. Gửi Email xác nhận đặt hàng (`EmailWorkerConsumer`)
*   **Topic:** `order-paid-topic` (groupId: `email-worker-group`).
*   **Logic xử lý:** Khi đơn hàng thanh toán thành công, consumer nhận event và tự động dùng `JavaMailSender` soạn thảo email thông báo định dạng HTML gửi đến hòm thư của khách hàng bao gồm: Mã đơn hàng, Số tiền thanh toán và Mã giao dịch.

### 5.4. Xử lý Hoa hồng Tiếp thị liên kết (`AffiliateOrderConsumers`)
*   **Topic:** Lắng nghe 3 topics (`order-paid-topic`, `order-delivered-topic`, `order-cancelled-topic`).
*   **Logic xử lý:**
    *   Khi đơn hàng được trả tiền (`ORDER_PAID`): Tra cứu thông tin cookie KOL giới thiệu lưu trên Redis. Nếu có, tính toán hoa hồng mặc định 10% và ghi nhận bản ghi hoa hồng trạng thái `PENDING`.
    *   Khi đơn hàng giao thành công (`ORDER_DELIVERED`): Cập nhật trạng thái hoa hồng sang `APPROVED`.
    *   Khi đơn hàng bị hủy (`ORDER_CANCELLED`): Cập nhật trạng thái hoa hồng sang `REJECTED`.

### 5.5. Gom lô tối ưu hóa ghi PostgreSQL (`AffiliateClickConsumer`)
*   **Topic:** `affiliate-clicks-topic` (groupId: `affiliate-click-group`).
*   **Buffer Batching Strategy:** Để tránh vắt kiệt I/O của database PostgreSQL khi có hàng ngàn click từ các liên kết KOL đổ về cùng lúc:
    *   Consumer nhận tin nhắn click và đẩy vào một Thread-safe List (`buffer`).
    *   **Cơ chế kích hoạt ghi:** Dữ liệu sẽ được ghi theo lô (Bulk Save) xuống DB khi số lượng sự kiện tích lũy đạt **50 clicks** hoặc định kỳ mỗi **3 giây** thông qua Scheduler (`@Scheduled(fixedRate = 3000)`).
