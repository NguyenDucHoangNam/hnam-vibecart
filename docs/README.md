# VibeCart Project Documentation Taxonomy

Thư mục tài liệu này được cấu trúc theo chuẩn công nghiệp dành cho các hệ thống lớn (Big Tech Standards), phân tách rõ ràng giữa mục tiêu nghiệp vụ (Product/Business specs), thiết kế kỹ thuật (Technical specifications), đặc tả tích hợp (API) và quy trình vận hành (Operations/Runbooks).

## Cấu trúc Thư mục

```text
docs/
├── business/               # Tài liệu Nghiệp vụ (PRD, Business Rules, User Flow)
│   ├── 01_identity_access.md
│   ├── 02_ecommerce_core.md
│   ├── 03_social_mini.md
│   ├── 04_shortlink_affiliate.md
│   ├── 05_realtime_chat.md
│   ├── 06_search_engine.md
│   └── 08_queue_background.md
│
├── technical/              # Tài liệu Kỹ thuật (Kiến trúc, DB Design, Sequence Diagrams, Concurrency)
│   ├── 01_identity_access_design.md
│   ├── 02_ecommerce_core_design.md
│   ├── 03_social_mini_design.md
│   ├── 04_shortlink_affiliate_design.md
│   ├── 05_realtime_chat_design.md
│   ├── 06_search_engine_design.md
│   ├── 07_media_storage_design.md
│   ├── 08_queue_background_design.md
│   ├── 09_redis_architecture_design.md
│   ├── 10_kafka_integration_design.md
│   ├── 11_realtime_websocket_design.md
│   └── 09_security_middleware_design.md
│
├── api/                    # Đặc tả API RESTful & WebSockets (Payloads, Endpoint Specs)
│   ├── 01_identity_access_api.md
│   ├── 02_ecommerce_api.md
│   ├── 03_social_api.md
│   ├── 04_affiliate_api.md
│   ├── 05_chat_websocket_api.md
│   ├── 06_search_api.md
│   └── 08_queue_background_api.md
│
└── operations/             # Hướng dẫn Vận hành & Cấu hình Hạ tầng (Docker, Setup Dev)
    └── 08_queue_background_ops.md
```

## Định nghĩa chi tiết

### 💼 1. `business/` (Nghiệp vụ - Nghiêng về Product/BA)
*   **Mục tiêu:** Trả lời cho câu hỏi **Cái gì (What)** và **Tại sao (Why)** hệ thống cần tính năng này.
*   **Nội dung chính:**
    *   Yêu cầu nghiệp vụ sản phẩm (Product Requirements Document - PRD).
    *   Quy trình trải nghiệm người dùng (User Flows & Journeys).
    *   Quy tắc kiểm soát nghiệp vụ (Business Rules Matrix).

### 🛠️ 2. `technical/` (Kỹ thuật - Nghiêng về Tech Lead/Developer)
*   **Mục tiêu:** Trả lời cho câu hỏi hệ thống giải quyết bài toán nghiệp vụ **Như thế nào (How)**.
*   **Nội dung chính:**
    *   Mô tả thiết kế hệ thống, sơ đồ lớp (Class), sơ đồ tuần tự (Sequence Diagram).
    *   Thiết kế cơ sở dữ liệu chi tiết cho PostgreSQL, MongoDB, Elasticsearch, Redis.
    *   Thuật toán đặc thù (Base62 cho link rút gọn, Collaborative Filtering cho recommendation, Fan-out cho Newsfeed).
    *   Kỹ thuật xử lý đồng thời (Concurrency control, Locks).

### 🔌 3. `api/` (Đặc tả Tích hợp)
*   **Mục tiêu:** Định nghĩa hợp đồng giao tiếp (Contracts) giữa Frontend-Backend hoặc với các bên thứ ba.
*   **Nội dung chính:**
    *   RESTful API specification (endpoints, request payloads, response payloads, errors).
    *   WebSocket/STOMP Topic specification (luồng realtime chat).

### 🚀 4. `operations/` (Vận hành - Nghiêng về SRE/DevOps/Developer Setup)
*   **Mục tiêu:** Hướng dẫn cách cài đặt, chạy hệ thống và giám sát sức khỏe ứng dụng.
*   **Nội dung chính:**
    *   Hướng dẫn chạy môi trường cục bộ (Local Development Setup Guide).
    *   Cấu hình Docker Compose cho hạ tầng phụ trợ (Kafka, Redis, Postgres, ES, Mongo).
    *   Hướng dẫn troubleshoot sự cố và đọc Metric (Prometheus/Actuator).
