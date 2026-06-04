# 🚀 Hướng dẫn Vận hành - Phân hệ 8: Hàng đợi & Xử lý Nền (Queue & Background Processing)

Tài liệu này hướng dẫn cách cấu hình, khởi chạy, giám sát và xử lý sự cố cho các thành phần xử lý nền của hệ thống **VibeCart** bao gồm: SMTP Email, Kafka Consumer/DLQ, ShedLock trên Redis, Spring Batch Job và Thread Pool xuất báo cáo.

---

## 📬 1. Cấu hình Email SMTP

### 1.1 Môi trường Local (Gmail SMTP)

Cấu hình mặc định trong `application-local.yaml`:

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME:no-reply@vibecart.com}
    password: ${MAIL_PASSWORD:password}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
```

**Thiết lập Gmail App Password:**
1.  Đăng nhập tài khoản Gmail sẽ dùng làm sender.
2.  Bật xác minh 2 bước (2-Step Verification) tại [myaccount.google.com/security](https://myaccount.google.com/security).
3.  Tạo App Password tại [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords).
4.  Cập nhật biến môi trường:
    ```bash
    export MAIL_USERNAME=your-email@gmail.com
    export MAIL_PASSWORD=xxxx-xxxx-xxxx-xxxx  # App Password 16 ký tự
    ```

### 1.2 Chuyển sang Production (Resend / AWS SES)

Chỉ cần thay đổi cấu hình YAML mà **không sửa code Java**:

```yaml
# Ví dụ: Chuyển sang Resend SMTP
spring:
  mail:
    host: smtp.resend.com
    port: 587
    username: resend
    password: ${RESEND_API_KEY}
```

---

## 🔄 2. Cấu hình Kafka Topics & Consumer Groups

### 2.1 Kafka Topics cho Module 8

| Topic | Mục đích | Auto-created |
| :--- | :--- | :--- |
| `notification-events` | Main topic cho sự kiện gửi email/thông báo | Có (bởi Spring Kafka) |
| `notification-events-0` | Retry topic lần 1 (delay 5s) | Có (bởi `@RetryableTopic`) |
| `notification-events-1` | Retry topic lần 2 (delay 10s) | Có (bởi `@RetryableTopic`) |
| `notification-events-2` | Retry topic lần 3 (delay 20s) | Có (bởi `@RetryableTopic`) |
| `notification-events-dlt` | Dead Letter Topic cho tin nhắn thất bại hoàn toàn | Có (bởi `@RetryableTopic`) |

### 2.2 Consumer Group

| Consumer Group ID | Class xử lý | Topic |
| :--- | :--- | :--- |
| `vibecart-notification-group` | `NotificationKafkaListener` | `notification-events` |

### 2.3 Cấu hình Kafka (`application-local.yaml`)

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: vibecart-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

### 2.4 Kiểm tra Topics trên Kafka

```bash
# Liệt kê tất cả topics
docker exec -it vibecart-kafka kafka-topics.sh --list --bootstrap-server localhost:9092

# Xem chi tiết topic notification-events
docker exec -it vibecart-kafka kafka-topics.sh --describe \
  --topic notification-events --bootstrap-server localhost:9092

# Đọc tin nhắn từ Dead Letter Topic (debug)
docker exec -it vibecart-kafka kafka-console-consumer.sh \
  --topic notification-events-dlt \
  --bootstrap-server localhost:9092 \
  --from-beginning \
  --property print.key=true \
  --property print.timestamp=true
```

---

## 💀 3. Xử lý Dead Letter Queue (DLQ Recovery)

### 3.1 Khi nào tin nhắn rơi vào DLQ?

Tin nhắn bị chuyển vào `notification-events-dlt` khi:
*   Gửi email thất bại sau **4 lần thử** (1 lần chính + 3 lần retry).
*   Nguyên nhân phổ biến: SMTP server down, email người nhận không hợp lệ, lỗi mạng kéo dài.

### 3.2 Quy trình xử lý DLQ thủ công

1.  **Đọc log lỗi:** Tìm kiếm keyword `CRITICAL - Message failed all retries` trong application log.
    ```bash
    grep "CRITICAL.*DLT" logs/vibecart.log
    ```

2.  **Đọc tin nhắn từ DLQ topic:**
    ```bash
    docker exec -it vibecart-kafka kafka-console-consumer.sh \
      --topic notification-events-dlt \
      --bootstrap-server localhost:9092 \
      --from-beginning
    ```

3.  **Khắc phục nguyên nhân gốc** (ví dụ: sửa cấu hình SMTP, kiểm tra kết nối mạng).

4.  **Replay tin nhắn** (nếu cần gửi lại):
    ```bash
    # Copy tin nhắn từ DLQ về main topic để xử lý lại
    docker exec -it vibecart-kafka kafka-console-producer.sh \
      --topic notification-events \
      --bootstrap-server localhost:9092
    # Paste JSON payload của tin nhắn cần gửi lại
    ```

---

## 🔒 4. ShedLock Troubleshooting

### 4.1 Kiểm tra Lock trên Redis

ShedLock lưu lock dưới dạng Redis key với namespace `vibecart:lock`:

```bash
# Kết nối Redis CLI
docker exec -it vibecart-redis redis-cli

# Tìm tất cả ShedLock keys
KEYS vibecart:lock:*

# Xem chi tiết lock của commission job
GET vibecart:lock:commissionJobLock

# Giải phóng lock thủ công (khi node bị sập giữ lock)
DEL vibecart:lock:commissionJobLock
```

### 4.2 Vấn đề phổ biến

| Vấn đề | Triệu chứng | Giải pháp |
| :--- | :--- | :--- |
| Batch Job không chạy | Log hiển thị "Lock already held" | Kiểm tra `KEYS vibecart:lock:*`, xóa lock nếu node cũ đã chết |
| Batch Job chạy trùng lặp | Hai kết quả đối soát cùng lúc | Kiểm tra Redis connection, đảm bảo tất cả nodes kết nối cùng Redis instance |
| Lock không tự giải phóng | Lock tồn tại quá 30 phút | Xóa thủ công bằng `DEL vibecart:lock:{name}`, kiểm tra `defaultLockAtMostFor` |

---

## ⚙️ 5. Kích hoạt Batch Job Thủ công (Manual Trigger)

### 5.1 Qua Admin REST API

```bash
# Kích hoạt đối soát hoa hồng
curl -X POST "http://localhost:8080/api/v1/admin/jobs/trigger?jobName=commissionSettlementJob" \
  -H "Authorization: Bearer {ADMIN_JWT_TOKEN}" \
  -H "Content-Type: application/json"
```

**Response thành công:**
```json
{
  "code": 1000,
  "message": "Batch Job [commissionSettlementJob] đã được kích hoạt chạy thủ công cưỡng bức thành công"
}
```

### 5.2 Các Job hợp lệ

| Job Name | Lịch tự động | Mô tả |
| :--- | :--- | :--- |
| `commissionSettlementJob` | 02:00 AM hàng ngày | Đối soát hoa hồng tiếp thị liên kết (PENDING → APPROVED/REJECTED) |

---

## 📊 6. Giám sát Thread Pool Báo cáo

### 6.1 Cấu hình Thread Pool hiện tại

| Tham số | Giá trị | Ý nghĩa |
| :--- | :--- | :--- |
| `corePoolSize` | 4 | Số thread hoạt động thường trực |
| `maxPoolSize` | 8 | Số thread tối đa khi queue đầy |
| `queueCapacity` | 50 | Số task chờ trong hàng đợi |
| `threadNamePrefix` | `ReportWorker-` | Tiền tố tên thread (dễ tìm trong log/thread dump) |

### 6.2 Giám sát qua Application Log

Theo dõi các log pattern sau để kiểm tra trạng thái tác vụ:

```bash
# Tác vụ bắt đầu
grep "Starting async report export worker" logs/vibecart.log

# Tác vụ hoàn thành
grep "Successfully completed report export task" logs/vibecart.log

# Tác vụ thất bại
grep "Failed to execute async report export" logs/vibecart.log
```

### 6.3 Kiểm tra trạng thái Task trong Database

```sql
-- Xem tất cả background tasks
SELECT id, user_id, task_type, status, result_url, error_message, created_at, updated_at 
FROM background_tasks 
ORDER BY created_at DESC 
LIMIT 20;

-- Xem các task đang chạy hoặc bị treo
SELECT * FROM background_tasks 
WHERE status IN ('PENDING', 'RUNNING') 
AND created_at < NOW() - INTERVAL '30 minutes';

-- Xem các task thất bại gần đây
SELECT id, error_message, created_at 
FROM background_tasks 
WHERE status = 'FAILED' 
ORDER BY created_at DESC 
LIMIT 10;
```

---

## 🐳 7. Docker & Hạ tầng Phụ trợ

### 7.1 Các Service Docker cần thiết cho Module 8

| Service | Port | Vai trò trong Module 8 |
| :--- | :--- | :--- |
| PostgreSQL | 5435 | Lưu trữ bảng `background_tasks`, `commissions` |
| Redis | 6379 | ShedLock distributed lock storage |
| Kafka | 9092 | Message queue cho notification events |
| MinIO (S3) | 9000 | Lưu trữ tệp Excel kết xuất báo cáo |

### 7.2 Khởi động hạ tầng

```bash
# Khởi động toàn bộ hạ tầng
docker-compose up -d

# Kiểm tra trạng thái
docker-compose ps

# Xem log Kafka broker
docker-compose logs -f kafka
```
